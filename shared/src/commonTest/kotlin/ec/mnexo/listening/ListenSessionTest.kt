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

/** Detector simulado: hay voz mientras llegan ventanas con 1f; al primer silencio entrega el tramo. */
private class FakeVad : VoiceActivityDetector {
    private val current = mutableListOf<Float>()
    private var ready: FloatArray? = null
    override fun accept(samples: FloatArray) {
        if (samples[0] > 0f) {
            current += samples.toList()
        } else if (current.isNotEmpty()) {
            ready = current.toFloatArray()
            current.clear()
        }
    }
    override fun isSpeaking() = current.isNotEmpty()
    override fun popSegment(): FloatArray? = ready.also { ready = null }
    override fun flush() {
        if (current.isNotEmpty()) {
            ready = current.toFloatArray()
            current.clear()
        }
    }
    override fun reset() {
        current.clear()
        ready = null
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
        assertEquals(10 * VAD_WINDOW, recognizedSamples, "Solo se reconoce el tramo con voz")
        assertTrue(mic.started && mic.stopped, "El micrófono siempre se cierra")
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
