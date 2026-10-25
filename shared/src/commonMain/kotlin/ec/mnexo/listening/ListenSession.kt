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

    /** Puede llamarse más de una vez; las implementaciones lo toleran. */
    fun stop()
}

class MicrophoneUnavailableException(val problem: ListenProblem, cause: Throwable? = null) : Exception(cause)

/** Un tramo de habla: [start] es la posición (en muestras) desde que se empezó a escuchar. */
class SpeechSegment(val start: Int, val samples: FloatArray)

/** Detector de actividad de voz: decide qué partes del audio son habla. */
interface VoiceActivityDetector {
    fun accept(samples: FloatArray)

    /** true mientras la persona está hablando. */
    fun isSpeaking(): Boolean

    /** Devuelve un tramo de habla ya terminado (la persona hizo una pausa), o null. */
    fun popSegment(): SpeechSegment?

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
    /**
     * Audio que se agrega antes y después del tramo detectado. El detector tarda unos milisegundos
     * en "darse cuenta" de que alguien habla: sin este margen se pierde la primera palabra, y
     * perder un "no" al inicio cambiaría el sentido de la orden.
     */
    val paddingBeforeMs: Int = 300,
    val paddingAfterMs: Int = 200,
)

/**
 * Captura una sola frase: espera a que la persona hable y devuelve el tramo de voz cuando termina,
 * o null si nadie habló. El micrófono siempre se cierra al terminar, incluso si se cancela.
 * El audio solo vive en memoria: nunca se guarda.
 */
suspend fun captureUtterance(
    input: AudioInput,
    vad: VoiceActivityDetector,
    timing: ListenTiming = ListenTiming(),
): FloatArray? = withContext(Dispatchers.Default) {
    val buffer = FloatArray(VAD_WINDOW)
    var samplesHeard = 0
    var speechStartedAt = -1
    val waitLimit = timing.waitForSpeechMs * LISTEN_SAMPLE_RATE / 1000
    val speechLimit = timing.maxSpeechMs * LISTEN_SAMPLE_RATE / 1000
    val padBefore = timing.paddingBeforeMs * LISTEN_SAMPLE_RATE / 1000
    val padAfter = timing.paddingAfterMs * LISTEN_SAMPLE_RATE / 1000
    // Todo lo escuchado en esta frase (como máximo ~30 s ≈ 2 MB en memoria; nunca se guarda en disco).
    val recorded = AudioBuffer(waitLimit + speechLimit + padBefore + padAfter)

    vad.reset()
    input.start()
    try {
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read <= 0) continue
            val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
            vad.accept(chunk)
            recorded.append(chunk)
            samplesHeard += read

            if (speechStartedAt < 0 && vad.isSpeaking()) speechStartedAt = samplesHeard
            if (speechStartedAt >= 0 && samplesHeard - speechStartedAt > speechLimit) vad.flush()

            vad.popSegment()?.let { segment ->
                val from = segment.start - padBefore
                val to = segment.start + segment.samples.size + padAfter
                return@withContext recorded.slice(from, to) ?: segment.samples
            }
            if (speechStartedAt < 0 && samplesHeard > waitLimit) return@withContext null
        }
        @Suppress("UNREACHABLE_CODE")
        error("inalcanzable")
    } finally {
        input.stop()
        vad.reset()
    }
}

/** Guarda en memoria el audio de una frase para poder recortarla con margen. */
private class AudioBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var size = 0

    fun append(chunk: FloatArray) {
        val count = minOf(chunk.size, capacity - size)
        chunk.copyInto(data, size, 0, count)
        size += count
    }

    /** Devuelve el audio entre [from] y [to] (recortados a lo disponible), o null si no hay nada. */
    fun slice(from: Int, to: Int): FloatArray? {
        val start = from.coerceIn(0, size)
        val end = to.coerceIn(start, size)
        return if (end > start) data.copyOfRange(start, end) else null
    }
}

/** Escucha una frase y la convierte en texto. */
suspend fun listenOnce(
    input: AudioInput,
    vad: VoiceActivityDetector,
    stt: SpeechToText,
    timing: ListenTiming = ListenTiming(),
    onRecognizing: () -> Unit = {},
): ListenResult {
    val segment = captureUtterance(input, vad, timing) ?: return ListenResult.NothingHeard
    onRecognizing()
    val text = withContext(Dispatchers.Default) { stt.transcribe(segment, LISTEN_SAMPLE_RATE).trim() }
    return if (text.isEmpty()) ListenResult.NothingHeard else ListenResult.Heard(text)
}
