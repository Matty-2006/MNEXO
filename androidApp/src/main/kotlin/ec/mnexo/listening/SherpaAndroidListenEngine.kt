package ec.mnexo.listening

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Micrófono y motor sherpa-onnx en Android. */
class SherpaAndroidListenEngine(context: Context) : ListenEngine {

    private val appContext = context.applicationContext

    override fun openMicrophone(): AudioInput = AndroidMicrophone()

    override fun createVoiceActivityDetector(): VoiceActivityDetector {
        val vad = Vad(
            assetManager = appContext.assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "listen/silero_vad.onnx",
                    threshold = 0.5f,
                    // Pausas de hasta 1,2 s no cortan la frase: muchas personas mayores hablan pausado.
                    minSilenceDuration = 1.2f,
                    minSpeechDuration = 0.25f,
                    windowSize = VAD_WINDOW,
                    maxSpeechDuration = 20f,
                ),
                sampleRate = LISTEN_SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            ),
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
            // El modelo descargado está en archivos normales, no en los assets.
            assetManager = null,
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = File(modelDir, "model.int8.onnx").absolutePath),
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
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
}

/** Micrófono de Android: audio mono a 16 kHz. El audio nunca se guarda. */
private class AndroidMicrophone : AudioInput {
    private var record: AudioRecord? = null

    // El permiso RECORD_AUDIO se pide en la pantalla antes de llegar aquí.
    @SuppressLint("MissingPermission")
    override fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            LISTEN_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                LISTEN_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, VAD_WINDOW * Float.SIZE_BYTES * 4),
            )
        } catch (e: SecurityException) {
            throw MicrophoneUnavailableException(ListenProblem.PERMISSION_DENIED, e)
        } catch (e: IllegalArgumentException) {
            throw MicrophoneUnavailableException(ListenProblem.NO_MICROPHONE, e)
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw MicrophoneUnavailableException(ListenProblem.NO_MICROPHONE)
        }
        audioRecord.startRecording()
        record = audioRecord
    }

    override fun read(buffer: FloatArray): Int {
        val audioRecord = record ?: return -1
        val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        if (read < 0) throw MicrophoneUnavailableException(ListenProblem.NO_MICROPHONE)
        return read
    }

    override fun stop() {
        val audioRecord = record ?: return
        record = null
        runCatching { audioRecord.stop() }
        audioRecord.release()
    }
}
