package ec.mnexo.listening

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

/** Micrófono y motor sherpa-onnx en Windows. [vadFile] es el detector de voz incluido en la app. */
class SherpaDesktopListenEngine(private val vadFile: File?) : ListenEngine {

    override fun openMicrophone(): AudioInput = DesktopMicrophone()

    override fun createVoiceActivityDetector(): VoiceActivityDetector {
        val model = requireNotNull(vadFile?.takeIf { it.isFile }) { "No se encontró el detector de voz" }
        val vad = Vad(
            VadModelConfig.Builder()
                .setSileroVadModelConfig(
                    SileroVadModelConfig.Builder()
                        .setModel(model.absolutePath)
                        .setThreshold(0.5f)
                        // Pausas de hasta 1,2 s no cortan la frase: muchas personas mayores hablan pausado.
                        .setMinSilenceDuration(1.2f)
                        .setMinSpeechDuration(0.25f)
                        .setWindowSize(VAD_WINDOW)
                        .setMaxSpeechDuration(20f)
                        .build(),
                )
                .setSampleRate(LISTEN_SAMPLE_RATE)
                .setNumThreads(1)
                .setProvider("cpu")
                .setDebug(false)
                .build(),
        )
        return object : VoiceActivityDetector {
            override fun accept(samples: FloatArray) = vad.acceptWaveform(samples)
            override fun isSpeaking() = vad.isSpeechDetected()
            override fun popSegment(): FloatArray? {
                if (vad.empty()) return null
                val segment = vad.front().samples
                vad.pop()
                return segment
            }
            override fun flush() = vad.flush()
            override fun reset() = vad.reset()
        }
    }

    override fun createSpeechToText(modelDir: String): SpeechToText {
        val recognizer = OfflineRecognizer(
            OfflineRecognizerConfig.Builder()
                .setOfflineModelConfig(
                    OfflineModelConfig.Builder()
                        .setNemo(
                            OfflineNemoEncDecCtcModelConfig.Builder()
                                .setModel(File(modelDir, "model.int8.onnx").absolutePath)
                                .build(),
                        )
                        .setTokens(File(modelDir, "tokens.txt").absolutePath)
                        .setNumThreads(2)
                        .setDebug(false)
                        .setProvider("cpu")
                        .build(),
                )
                .setDecodingMethod("greedy_search")
                .build(),
        )
        return SpeechToText { samples, sampleRate ->
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                recognizer.decode(stream)
                recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
        }
    }

    companion object {
        fun findVadFile(): File? {
            val candidates = listOfNotNull(
                System.getProperty("mnexo.vad.file")?.let(::File),
                System.getProperty("compose.application.resources.dir")?.let { File(it, "listen/silero_vad.onnx") },
            )
            return candidates.firstOrNull { it.isFile }
        }
    }
}

/** Micrófono de Windows: audio mono a 16 kHz y 16 bits. El audio nunca se guarda. */
private class DesktopMicrophone : AudioInput {
    private val format = AudioFormat(LISTEN_SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private var line: TargetDataLine? = null
    private var bytes = ByteArray(0)

    override fun start() {
        val targetLine = try {
            AudioSystem.getTargetDataLine(format).apply { open(format, VAD_WINDOW * 2 * 8) }
        } catch (e: LineUnavailableException) {
            throw MicrophoneUnavailableException(ListenProblem.NO_MICROPHONE, e)
        } catch (e: IllegalArgumentException) {
            throw MicrophoneUnavailableException(ListenProblem.NO_MICROPHONE, e)
        } catch (e: SecurityException) {
            throw MicrophoneUnavailableException(ListenProblem.PERMISSION_DENIED, e)
        }
        targetLine.start()
        line = targetLine
    }

    override fun read(buffer: FloatArray): Int {
        val targetLine = line ?: return -1
        if (bytes.size != buffer.size * 2) bytes = ByteArray(buffer.size * 2)
        val read = targetLine.read(bytes, 0, bytes.size)
        val samples = read / 2
        for (i in 0 until samples) {
            val value = (bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)
            buffer[i] = value.toShort() / 32768f
        }
        return samples
    }

    override fun stop() {
        val targetLine = line ?: return
        line = null
        targetLine.stop()
        targetLine.close()
    }
}
