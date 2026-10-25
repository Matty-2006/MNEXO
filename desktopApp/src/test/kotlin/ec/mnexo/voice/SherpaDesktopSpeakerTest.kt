package ec.mnexo.voice

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Prueba real del motor: genera audio en español con la voz incluida (sin reproducirlo). */
class SherpaDesktopSpeakerTest {

    @Test
    fun generaAudioEnEspanolConLaVozPropia() = runBlocking {
        val voiceDir = assertNotNull(SherpaDesktopSpeaker.findVoiceDir(), "No se encontró la voz propia")
        val speaker = SherpaDesktopSpeaker(voiceDir)

        val audio = speaker.synthesize("Hola, Rosa. Soy MNEXO. ¿En qué te ayudo?", SpeechRate.NORMAL)

        assertEquals(22_050, audio.sampleRate)
        val seconds = audio.samples.size / audio.sampleRate.toFloat()
        assertTrue(seconds in 1.0f..10.0f, "Duración inesperada: $seconds s")
        assertTrue(audio.samples.any { kotlin.math.abs(it) > 0.05f }, "El audio está en silencio")
    }

    @Test
    fun hablarMasDespacioProduceUnAudioMasLargo() = runBlocking {
        val speaker = SherpaDesktopSpeaker(assertNotNull(SherpaDesktopSpeaker.findVoiceDir()))
        val text = "Buenos días. ¿Cómo estás?"

        val normal = speaker.synthesize(text, SpeechRate.NORMAL).samples.size
        val slow = speaker.synthesize(text, SpeechRate.MUY_LENTA).samples.size

        assertTrue(slow > normal, "Más despacio debería durar más ($slow vs $normal muestras)")
    }

    @Test
    fun convierteAudioA16BitsSinDesbordarse() {
        val bytes = SherpaDesktopSpeaker.toPcm16(floatArrayOf(0f, 1f, -1f, 2f))

        assertEquals(8, bytes.size)
        assertEquals(0, bytes[0].toInt())
        // 1.0 → 32767 = 0x7FFF; un valor fuera de rango (2.0) se recorta al máximo.
        assertEquals(0xFF.toByte(), bytes[2])
        assertEquals(0x7F.toByte(), bytes[3])
        assertEquals(bytes[2], bytes[6])
        assertEquals(bytes[3], bytes[7])
    }
}
