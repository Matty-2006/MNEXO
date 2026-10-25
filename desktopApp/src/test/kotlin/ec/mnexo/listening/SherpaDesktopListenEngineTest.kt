package ec.mnexo.listening

import ec.mnexo.voice.SherpaDesktopSpeaker
import ec.mnexo.voice.SpeechRate
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba real de extremo a extremo en la PC: la voz propia de MNEXO dice una frase, y el oído
 * (detector de voz Silero + reconocimiento NVIDIA FastConformer) tiene que entenderla.
 */
class SherpaDesktopListenEngineTest {

    private val asrDir = assertNotNull(System.getProperty("mnexo.asr.dir"), "Falta el modelo de prueba")
    private val engine = SherpaDesktopListenEngine(SherpaDesktopListenEngine.findVadFile())

    /** Micrófono simulado que "escucha" un audio ya grabado, con silencio antes y después. */
    private class PlaybackInput(audio: FloatArray) : AudioInput {
        private val samples = FloatArray(LISTEN_SAMPLE_RATE) + audio + FloatArray(3 * LISTEN_SAMPLE_RATE)
        private var position = 0
        override fun start() {}
        override fun read(buffer: FloatArray): Int {
            for (i in buffer.indices) buffer[i] = samples.getOrElse(position++) { 0f }
            return buffer.size
        }
        override fun stop() {}
    }

    private fun speak(text: String): FloatArray = runBlocking {
        val voice = SherpaDesktopSpeaker(assertNotNull(SherpaDesktopSpeaker.findVoiceDir()))
        val audio = voice.synthesize(text, SpeechRate.PAUSADA)
        resample(audio.samples, audio.sampleRate, LISTEN_SAMPLE_RATE)
    }

    /** Conversión simple de frecuencia de muestreo (22 050 Hz de la voz → 16 000 Hz del oído). */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        val output = FloatArray((input.size.toLong() * to / from).toInt())
        for (i in output.indices) {
            val position = i.toDouble() * from / to
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = input.getOrElse(index) { 0f }
            val b = input.getOrElse(index + 1) { a }
            output[i] = a + (b - a) * fraction
        }
        return output
    }

    @Test
    fun entiendeUnaFraseCompletaConDetectorDeVoz(): Unit = runBlocking {
        val result = listenOnce(
            input = PlaybackInput(speak("Quiero llamar a mi hija María")),
            vad = engine.createVoiceActivityDetector(),
            stt = engine.createSpeechToText(asrDir),
        )

        val heard = assertIs<ListenResult.Heard>(result).text.lowercase()
        println("Reconocido: $heard")
        assertTrue("llamar" in heard && "hija" in heard, "No entendió la frase: «$heard»")
    }

    @Test
    fun conSoloSilencioNoEntiendeNada(): Unit = runBlocking {
        val result = listenOnce(
            input = PlaybackInput(FloatArray(0)),
            vad = engine.createVoiceActivityDetector(),
            stt = engine.createSpeechToText(asrDir),
            timing = ListenTiming(waitForSpeechMs = 2_000),
        )

        assertIs<ListenResult.NothingHeard>(result)
    }

    @Test
    fun elDetectorDeVozVieneIncluidoEnLaApp() {
        assertTrue(File(assertNotNull(SherpaDesktopListenEngine.findVadFile()).path).length() > 100_000)
    }
}
