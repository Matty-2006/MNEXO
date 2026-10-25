package ec.mnexo.voiceprint

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoicePrintsTest {

    private fun close(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 1e-4f, "$expected ≠ $actual")

    @Test
    fun lasHuellasIgualesSeParecenAlCien() {
        close(1f, VoicePrints.similarity(floatArrayOf(3f, 4f), floatArrayOf(6f, 8f)))
    }

    @Test
    fun lasHuellasPerpendicularesNoSeParecen() {
        close(0f, VoicePrints.similarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
    }

    @Test
    fun laHuellaFinalEsElPromedioNormalizado() {
        val profile = VoicePrints.average(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))

        close(0.7071f, profile[0])
        close(0.7071f, profile[1])
    }

    @Test
    fun frasesParecidasDanMayorConsistencia() {
        val consistent = VoicePrints.consistency(listOf(floatArrayOf(1f, 0.1f), floatArrayOf(1f, 0f), floatArrayOf(1f, -0.1f)))
        val inconsistent = VoicePrints.consistency(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(-1f, 1f)))

        assertTrue(consistent > VoicePrints.GOOD_CONSISTENCY)
        assertTrue(inconsistent < VoicePrints.GOOD_CONSISTENCY)
    }

    @Test
    fun laHuellaSeGuardaYSeRecuperaSinPerderDatos() {
        val profile = floatArrayOf(0.123f, -4.5f, 1e-7f, Float.MAX_VALUE)

        val restored = VoicePrints.fromBytes(VoicePrints.toBytes(profile))

        assertTrue(profile.contentEquals(restored))
        assertEquals(16, VoicePrints.toBytes(profile).size)
    }

    @Test
    fun hayCincoFrasesDeRegistro() {
        assertEquals(5, VoicePrints.ENROLLMENT_PHRASES.size)
    }
}
