package ec.mnexo

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedCommonTest {

    @Test
    fun saludaConUnNombrePersonalizado() {
        assertEquals("¡Hola, Rosa!", sayHello("  Rosa "))
    }

    @Test
    fun saludaSinNombreSiEstaVacio() {
        assertEquals("¡Hola!", sayHello("   "))
    }
}
