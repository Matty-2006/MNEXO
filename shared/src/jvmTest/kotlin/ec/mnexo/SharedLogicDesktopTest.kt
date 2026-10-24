package ec.mnexo

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLogicDesktopTest {

    @Test
    fun enEscritorioMuestraElSistemaOperativo() {
        assertEquals(System.getProperty("os.name"), getPlatform().name)
    }
}
