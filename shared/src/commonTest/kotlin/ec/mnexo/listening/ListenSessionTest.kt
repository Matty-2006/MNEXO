package ec.mnexo.listening

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Micrófono simulado: entrega ventanas de audio; las marcadas con 1f son "voz". */
private class FakeMicrophone(private val windows: List<Float>) : AudioInput {
    var started = false
    var stopped = false
    private var index = 0
    override fun start() {
        started = true
    }
    override fun read(buffer: FloatArray): Int {
        buffer.fill(windows.getOrElse(index++) { 0f })
        return buffer.size
    }
    override fun stop() {
        stopped = true
    }
}

/**
 * Detector simulado: hay voz mientras llegan ventanas con valor > 0; al primer silencio entrega el tramo.
 * Como un detector real, "se da cuenta" una ventana tarde: la primera ventana de voz no entra al tramo.
 */
private class FakeVad : VoiceActivityDetector {
    private val current = mutableListOf<Float>()
    private var ready: SpeechSegment? = null
    private var position = 0
    private var currentStart = 0
    private var missedFirst = false
    override fun accept(samples: FloatArray) {
        if (samples[0] > 0f) {
            if (!missedFirst) {
                missedFirst = true // el detector reacciona tarde
            } else {
                if (current.isEmpty()) currentStart = position
                current += samples.toList()
            }
        } else if (current.isNotEmpty()) {
            flush()
        }
        position += samples.size
    }
    override fun isSpeaking() = current.isNotEmpty()
    override fun popSegment(): SpeechSegment? = ready.also { ready = null }
    override fun flush() {
        if (current.isNotEmpty()) {
            ready = SpeechSegment(currentStart, current.toFloatArray())
            current.clear()
        }
    }
    override fun reset() {
        current.clear()
        ready = null
        position = 0
        missedFirst = false
    }
}

class ListenSessionTest {

    private val silence = List(5) { 0f }
    private val speech = List(10) { 1f }

    @Test
    fun cuandoLaPersonaHablaDevuelveElTexto() = runTest {
        val mic = FakeMicrophone(silence + speech + silence)
        var recognizedSamples = 0
        val stt = SpeechToText { samples, _ ->
            recognizedSamples = samples.size
            "  quiero llamar a mi hija  "
        }

        val result = listenOnce(mic, FakeVad(), stt)

        assertEquals(ListenResult.Heard("quiero llamar a mi hija"), result)
        assertTrue(recognizedSamples >= 10 * VAD_WINDOW, "Se reconoce todo el tramo con voz")
        assertTrue(mic.started && mic.stopped, "El micrófono siempre se cierra")
    }

    @Test
    fun noSePierdeElComienzoDeLaFraseAunqueElDetectorReaccioneTarde() = runTest {
        // La primera ventana de voz (valor 2) es la palabra "no"; el detector simulado la pasa por alto.
        val mic = FakeMicrophone(silence + listOf(2f) + speech + silence)
        var captured = FloatArray(0)

        listenOnce(mic, FakeVad(), SpeechToText { samples, _ ->
            captured = samples
            "no llames"
        })

        assertTrue(captured.any { it == 2f }, "El margen previo debe recuperar la primera palabra")
    }

    @Test
    fun siNadieHablaTerminaSinResultado() = runTest {
        val mic = FakeMicrophone(emptyList())
        val stt = SpeechToText { _, _ -> error("No debería reconocer nada") }

        val result = listenOnce(mic, FakeVad(), stt, ListenTiming(waitForSpeechMs = 500))

        assertEquals(ListenResult.NothingHeard, result)
        assertTrue(mic.stopped)
    }

    @Test
    fun siElReconocimientoQuedaVacioNoInventaTexto() = runTest {
        val result = listenOnce(FakeMicrophone(speech + silence), FakeVad(), SpeechToText { _, _ -> "   " })

        assertEquals(ListenResult.NothingHeard, result)
    }

    @Test
    fun unaFraseDemasiadoLargaSeCortaYSeReconoce() = runTest {
        // Ruido constante: el detector nunca ve una pausa, pero la sesión corta al límite.
        val mic = FakeMicrophone(List(200) { 1f })

        val result = listenOnce(mic, FakeVad(), SpeechToText { _, _ -> "hola" }, ListenTiming(maxSpeechMs = 1_000))

        assertEquals(ListenResult.Heard("hola"), result)
    }
}
