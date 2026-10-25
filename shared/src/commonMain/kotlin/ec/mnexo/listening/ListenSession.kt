package ec.mnexo.listening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

const val LISTEN_SAMPLE_RATE = 16_000

/** Silero VAD analiza el audio en ventanas de 512 muestras (32 ms a 16 kHz). */
const val VAD_WINDOW = 512

/** Micrófono: entrega audio mono a 16 kHz con muestras entre -1 y 1. */
interface AudioInput {
    /** Lanza [MicrophoneUnavailableException] si no se puede abrir. */
    fun start()

    /** Llena [buffer] (bloquea hasta tener audio) y devuelve cuántas muestras leyó. */
    fun read(buffer: FloatArray): Int

    fun stop()
}

class MicrophoneUnavailableException(val problem: ListenProblem, cause: Throwable? = null) : Exception(cause)

/** Detector de actividad de voz: decide qué partes del audio son habla. */
interface VoiceActivityDetector {
    fun accept(samples: FloatArray)

    /** true mientras la persona está hablando. */
    fun isSpeaking(): Boolean

    /** Devuelve un tramo de habla ya terminado (la persona hizo una pausa), o null. */
    fun popSegment(): FloatArray?

    /** Da por terminado el tramo actual aunque la persona no haya hecho pausa. */
    fun flush()

    fun reset()
}

/** Convierte un tramo de audio en texto. */
fun interface SpeechToText {
    fun transcribe(samples: FloatArray, sampleRate: Int): String
}

/** Tiempos de escucha, pensados para personas que hablan pausado. */
data class ListenTiming(
    /** Si en este tiempo no empieza a hablar, se deja de escuchar. */
    val waitForSpeechMs: Int = 8_000,
    /** Una frase no puede durar más que esto (evita escuchar para siempre si hay ruido constante). */
    val maxSpeechMs: Int = 20_000,
)

/**
 * Escucha una sola frase: espera a que la persona hable, detecta cuando termina y la convierte en texto.
 * El micrófono siempre se cierra al terminar, incluso si se cancela. No se guarda ningún audio.
 */
suspend fun listenOnce(
    input: AudioInput,
    vad: VoiceActivityDetector,
    stt: SpeechToText,
    timing: ListenTiming = ListenTiming(),
    onRecognizing: () -> Unit = {},
): ListenResult = withContext(Dispatchers.Default) {
    val buffer = FloatArray(VAD_WINDOW)
    var samplesHeard = 0
    var speechStartedAt = -1
    val waitLimit = timing.waitForSpeechMs * LISTEN_SAMPLE_RATE / 1000
    val speechLimit = timing.maxSpeechMs * LISTEN_SAMPLE_RATE / 1000

    vad.reset()
    input.start()
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read <= 0) continue
            vad.accept(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
            samplesHeard += read

            if (speechStartedAt < 0 && vad.isSpeaking()) speechStartedAt = samplesHeard
            if (speechStartedAt >= 0 && samplesHeard - speechStartedAt > speechLimit) vad.flush()

            val segment = vad.popSegment()
            if (segment != null) {
                input.stop()
                onRecognizing()
                val text = stt.transcribe(segment, LISTEN_SAMPLE_RATE).trim()
                return@withContext if (text.isEmpty()) ListenResult.NothingHeard else ListenResult.Heard(text)
            }
            if (speechStartedAt < 0 && samplesHeard > waitLimit) return@withContext ListenResult.NothingHeard
        }
        @Suppress("UNREACHABLE_CODE")
        error("inalcanzable")
    } finally {
        // stop() puede llamarse dos veces (también antes de reconocer); las implementaciones lo toleran.
        input.stop()
        vad.reset()
    }
}
