package ec.mnexo.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class UserSettingsTest {

    @Test
    fun limpiaEspaciosRepetidosYDeLosBordes() {
        assertEquals("María José", sanitizeName("   María    José  "))
    }

    @Test
    fun quitaNumerosYSimbolos() {
        assertEquals("Ana", sanitizeName("Ana123!!"))
    }

    @Test
    fun conservaApostrofosGuionesYTildes() {
        assertEquals("O'Brien Ñusta-Pérez", sanitizeName("O'Brien Ñusta-Pérez"))
    }

    @Test
    fun limitaLaLongitudDelNombre() {
        assertEquals(MAX_NAME_LENGTH, sanitizeName("a".repeat(100)).length)
    }

    @Test
    fun elAsistenteSinNombreVuelveAMnexo() {
        assertEquals(DEFAULT_ASSISTANT_NAME, sanitizeAssistantName("  123 "))
    }

    @Test
    fun elAsistentePuedeTenerOtroNombre() {
        assertEquals("Lucía", sanitizeAssistantName(" Lucía "))
    }

    @Test
    fun unTamanoDeLetraDesconocidoUsaGrande() {
        assertEquals(TextSize.GRANDE, TextSize.fromStored("GIGANTE"))
        assertEquals(TextSize.GRANDE, TextSize.fromStored(null))
        assertEquals(TextSize.ENORME, TextSize.fromStored("ENORME"))
    }
}
