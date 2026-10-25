package ec.mnexo.listening

import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Prueba la descarga con archivos locales (URL file://) en lugar de internet. */
class HttpModelStoreTest {

    private val temp: File = Files.createTempDirectory("mnexo-model").toFile()
    private val source = File(temp, "origen").apply { mkdirs() }
    private val target = File(temp, "modelo")

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun sourceFile(name: String, content: String): ModelFile {
        val file = File(source, name).apply { writeText(content) }
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return ModelFile(name, file.toURI().toString(), sha, file.length())
    }

    @Test
    fun descargaVerificaYMarcaComoInstalado() = runBlocking {
        val files = listOf(sourceFile("model.int8.onnx", "modelo de prueba"), sourceFile("tokens.txt", "a 1\nb 2"))
        val store = HttpModelStore(target, files)
        val progress = mutableListOf<Float>()

        assertFalse(store.isInstalled)
        store.download { progress += it }

        assertTrue(store.isInstalled)
        assertEquals("modelo de prueba", File(target, "model.int8.onnx").readText())
        assertEquals(1f, progress.last())
    }

    @Test
    fun unArchivoAlteradoSeRechazaYNoQuedaInstalado() = runBlocking {
        val genuine = sourceFile("model.int8.onnx", "modelo original")
        val tampered = genuine.copy(sha256 = "0".repeat(64))
        val store = HttpModelStore(target, listOf(tampered))

        assertFailsWith<IOException> { store.download {} }

        assertFalse(store.isInstalled)
        assertFalse(File(target, "model.int8.onnx").exists(), "No debe quedar un archivo sin verificar")
    }

    @Test
    fun siYaEstaDescargadoNoVuelveADescargar() = runBlocking {
        val file = sourceFile("tokens.txt", "a 1")
        HttpModelStore(target, listOf(file)).download {}
        File(source, "tokens.txt").delete() // si intentara descargar de nuevo, fallaría

        HttpModelStore(target, listOf(file)).download {}

        assertTrue(HttpModelStore(target, listOf(file)).isInstalled)
    }
}
