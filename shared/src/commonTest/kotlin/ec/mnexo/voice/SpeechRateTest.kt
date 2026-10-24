package ec.mnexo.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechRateTest {

    @Test
    fun masDespacioBajaUnNivel() {
        assertEquals(SpeechRate.NORMAL, SpeechRate.RAPIDA.slower())
        assertEquals(SpeechRate.PAUSADA, SpeechRate.NORMAL.slower())
        assertEquals(SpeechRate.MUY_LENTA, SpeechRate.PAUSADA.slower())
    }

    @Test
    fun laMasLentaNoBajaMas() {
        assertEquals(SpeechRate.MUY_LENTA, SpeechRate.MUY_LENTA.slower())
        assertTrue(SpeechRate.MUY_LENTA.isSlowest)
        assertFalse(SpeechRate.PAUSADA.isSlowest)
    }

    @Test
    fun unaVelocidadDesconocidaUsaPausada() {
        assertEquals(SpeechRate.PAUSADA, SpeechRate.fromStored("SUPERSONICA"))
        assertEquals(SpeechRate.PAUSADA, SpeechRate.fromStored(null))
    }
}
