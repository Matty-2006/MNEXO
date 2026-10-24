package ec.mnexo

class JVMPlatform : Platform {
    // En la PC, "os.name" devuelve por ejemplo "Windows 11".
    override val name: String = System.getProperty("os.name") ?: "escritorio"
}

actual fun getPlatform(): Platform = JVMPlatform()
