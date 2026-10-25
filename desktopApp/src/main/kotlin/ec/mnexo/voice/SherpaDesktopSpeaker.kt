package ec.mnexo.voice

import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsCallback
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Voz propia de MNEXO en Windows: sherpa-onnx con la voz Piper es_MX "claude", incluida en la app.
 * Funciona aunque Windows no tenga voces en español y sin internet.
 *
 * @param voiceDir carpeta con model.onnx, tokens.txt y espeak-ng-data; null si no se encontró.
 */
class SherpaDesktopSpeaker(
    private val voiceDir: File?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Speaker {

    private val _state = MutableStateFlow<SpeakerState>(
        if (voiceDir.hasVoice()) SpeakerState.Idle else SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR),
    )
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private val playLock = Mutex()
    private var engine: OfflineTts? = null

    /** Número de la frase actual. Cada "speak" o "stop" lo cambia: así una frase vieja sabe que debe callarse. */
    @Volatile private var current = 0L
    @Volatile private var line: SourceDataLine? = null

    override fun speak(text: String, rate: SpeechRate) {
        if (!voiceDir.hasVoice()) return
        stop()
        val id = current
        scope.launch {
            val tts = try {
                loadEngine()
            } catch (e: Throwable) {
                System.err.println("MNEXO: no se pudo cargar la voz propia: $e")
                _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
                return@launch
            }
            playLock.withLock {
                if (id == current) play(tts, text, rate, id)
            }
        }
    }

    private fun play(tts: OfflineTts, text: String, rate: SpeechRate, id: Long) {
        // Audio de 16 bits, mono: el formato que acepta cualquier tarjeta de sonido.
        val format = AudioFormat(tts.sampleRate.toFloat(), 16, 1, true, false)
        val output = try {
            AudioSystem.getSourceDataLine(format).apply { open(format) }
        } catch (e: Exception) {
            System.err.println("MNEXO: no hay salida de audio disponible: $e")
            _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
            return
        }
        line = output
        try {
            output.start()
            _state.value = SpeakerState.Speaking
            tts.generateWithCallback(text, 0, rate.multiplier, OfflineTtsCallback { samples ->
                if (id != current) {
                    0
                } else {
                    val bytes = toPcm16(samples)
                    output.write(bytes, 0, bytes.size)
                    1
                }
            })
            if (id == current) output.drain()
        } catch (e: Exception) {
            System.err.println("MNEXO: error al hablar con la voz propia: $e")
            if (id == current) _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
            return
        } finally {
            if (line === output) line = null
            output.close()
        }
        if (id == current) _state.value = SpeakerState.Idle
    }

    override fun stop() {
        current++
        line?.let { runCatching { it.stop(); it.flush() } }
        if (_state.value == SpeakerState.Speaking || _state.value == SpeakerState.Preparing) {
            _state.value = SpeakerState.Idle
        }
    }

    override fun openVoiceSetup(): Boolean = false

    /** Genera el audio completo sin reproducirlo (para pruebas automáticas). */
    internal suspend fun synthesize(text: String, rate: SpeechRate): GeneratedAudio =
        loadEngine().generate(text, 0, rate.multiplier)

    private suspend fun loadEngine(): OfflineTts = engineLock.withLock {
        engine ?: run {
            _state.value = SpeakerState.Preparing
            val dir = requireNotNull(voiceDir)
            val config = OfflineTtsConfig.Builder()
                .setModel(
                    OfflineTtsModelConfig.Builder()
                        .setVits(
                            OfflineTtsVitsModelConfig.Builder()
                                .setModel(File(dir, "model.onnx").absolutePath)
                                .setTokens(File(dir, "tokens.txt").absolutePath)
                                .setDataDir(File(dir, "espeak-ng-data").absolutePath)
                                .build(),
                        )
                        .setNumThreads(2)
                        .setDebug(false)
                        .setProvider("cpu")
                        .build(),
                )
                .build()
            OfflineTts(config).also {
                engine = it
                _state.value = SpeakerState.Idle
            }
        }
    }

    companion object {
        /** Busca la voz entre los recursos de la app (al ejecutar desde Gradle o ya instalada). */
        fun findVoiceDir(): File? {
            val candidates = listOfNotNull(
                System.getProperty("mnexo.voice.dir")?.let(::File),
                System.getProperty("compose.application.resources.dir")?.let { File(it, "voice/es_MX-claude") },
            )
            return candidates.firstOrNull { it.hasVoice() }
        }

        private fun File?.hasVoice(): Boolean = this != null && File(this, "model.onnx").isFile

        /** Convierte muestras de -1..1 a audio de 16 bits (little endian). */
        internal fun toPcm16(samples: FloatArray): ByteArray {
            val bytes = ByteArray(samples.size * 2)
            samples.forEachIndexed { i, sample ->
                val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                bytes[2 * i] = (value and 0xFF).toByte()
                bytes[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
            }
            return bytes
        }
    }
}
