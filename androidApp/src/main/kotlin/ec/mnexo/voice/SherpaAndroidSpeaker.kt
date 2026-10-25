package ec.mnexo.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Voz propia de MNEXO en Android: sherpa-onnx con la voz Piper es_MX "claude", incluida en la app.
 * Funciona en cualquier celular, aunque no tenga voces en español y sin internet.
 *
 * El motor se carga recién la primera vez que se necesita (así no gasta memoria si el celular
 * ya tiene una voz en español) y el audio se reproduce mientras se genera.
 */
class SherpaAndroidSpeaker(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Speaker {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<SpeakerState>(SpeakerState.Idle)
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private val engineLock = Mutex()
    private val playLock = Mutex()
    private var engine: OfflineTts? = null

    /** Número de la frase actual. Cada "speak" o "stop" lo cambia: así una frase vieja sabe que debe callarse. */
    @Volatile private var current = 0L
    @Volatile private var track: AudioTrack? = null

    override fun speak(text: String, rate: SpeechRate) {
        stop()
        val id = current
        scope.launch {
            val tts = try {
                engineLock.withLock {
                    engine ?: run {
                        _state.value = SpeakerState.Preparing
                        createEngine().also { engine = it }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "No se pudo cargar la voz propia", e)
                _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
                return@launch
            }
            // Una sola frase a la vez: si la anterior aún se está deteniendo, se espera a que suelte el motor.
            playLock.withLock {
                if (id == current) play(tts, text, rate, id)
            }
        }
    }

    private suspend fun play(tts: OfflineTts, text: String, rate: SpeechRate, id: Long) {
        val sampleRate = tts.sampleRate()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack

        var framesWritten = 0
        try {
            audioTrack.play()
            _state.value = SpeakerState.Speaking
            // sherpa-onnx entrega el audio por trozos; devolver 0 le pide que se detenga.
            tts.generateWithCallback(text, 0, rate.multiplier) { samples ->
                if (id != current) {
                    0
                } else {
                    audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    framesWritten += samples.size
                    1
                }
            }
            // Esperar a que termine de sonar lo que quedó en el búfer.
            while (id == current && audioTrack.playbackHeadPosition < framesWritten) delay(50)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al hablar con la voz propia", e)
            if (id == current) _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
            return
        } finally {
            if (track === audioTrack) track = null
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
        if (id == current) _state.value = SpeakerState.Idle
    }

    override fun stop() {
        current++
        track?.let { runCatching { it.pause(); it.flush() } }
        if (_state.value == SpeakerState.Speaking || _state.value == SpeakerState.Preparing) {
            _state.value = SpeakerState.Idle
        }
    }

    // La voz propia no necesita instalarse.
    override fun openVoiceSetup(): Boolean = false

    private fun createEngine(): OfflineTts {
        val espeakDir = copyEspeakData()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$VOICE_ASSETS/model.onnx",
                    tokens = "$VOICE_ASSETS/tokens.txt",
                    dataDir = espeakDir.absolutePath,
                ),
                // 2 hilos: rápido en celulares modernos sin saturar los económicos.
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        // El modelo se lee directo de los assets; los datos de pronunciación deben ser archivos reales.
        return OfflineTts(assetManager = appContext.assets, config = config)
    }

    /** Copia los datos de pronunciación (≈1 MB) desde los assets a la carpeta privada, una sola vez. */
    private fun copyEspeakData(): File {
        val target = File(appContext.filesDir, "$VOICE_ASSETS/espeak-ng-data")
        val marker = File(target, ".copiado-v1")
        if (marker.exists()) return target
        target.deleteRecursively()
        copyAssetDir("$VOICE_ASSETS/espeak-ng-data", target)
        marker.writeText("ok")
        return target
    }

    private fun copyAssetDir(assetPath: String, target: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            children.forEach { copyAssetDir("$assetPath/$it", File(target, it)) }
        }
    }

    private companion object {
        const val TAG = "MnexoVozPropia"
        const val VOICE_ASSETS = "voice/es_MX-claude"
    }
}
