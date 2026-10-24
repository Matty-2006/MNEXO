package ec.mnexo.voice

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSpeakerTest {

    private fun decodeScript(encoded: String) = String(Base64.getDecoder().decode(encoded), Charsets.UTF_16LE)

    @Test
    fun elTextoViajaCodificadoYNoComoParteDelComando() {
        // Un texto malicioso que intenta cerrar la comilla y ejecutar otra orden.
        val malicious = "Hola'; Remove-Item C:\\ -Recurse; '"

        val script = decodeScript(WindowsSpeaker.buildEncodedCommand(malicious, SpeechRate.NORMAL))

        assertFalse(script.contains("Remove-Item"), "El texto no debe aparecer en claro dentro del script")
        val textBase64 = Base64.getEncoder().encodeToString(malicious.toByteArray(Charsets.UTF_8))
        assertTrue(script.contains("FromBase64String('$textBase64')"))
    }

    @Test
    fun laVelocidadUsaPuntoDecimalSinImportarElIdiomaDelEquipo() {
        val script = decodeScript(WindowsSpeaker.buildEncodedCommand("Hola", SpeechRate.PAUSADA))

        assertTrue(script.contains("SpeakingRate = 0.85"))
    }
}
