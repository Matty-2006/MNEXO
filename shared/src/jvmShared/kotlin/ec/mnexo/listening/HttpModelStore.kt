package ec.mnexo.listening

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Un archivo del modelo, con su huella SHA-256 para comprobar que llegó completo y sin alterar. */
data class ModelFile(val name: String, val url: String, val sha256: String, val size: Long)

object SpeechModels {
    // NVIDIA FastConformer CTC español (CC-BY-4.0), convertido por el proyecto sherpa-onnx.
    // Se fija una versión exacta del repositorio para que el archivo nunca cambie sin que lo sepamos.
    private const val NEMO_ES_REPO =
        "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-fast-conformer-ctc-es-1424-int8/resolve/05a9908f7571fd39c774445806dfb8189bcdb43d"

    const val NEMO_ES_DIR_NAME = "asr-es-nemo-ctc"

    val NEMO_ES_CTC = listOf(
        ModelFile(
            name = "model.int8.onnx",
            url = "$NEMO_ES_REPO/model.int8.onnx",
            sha256 = "9539b206ba7cb46231e24eb1f1d7269370bfd45209c549d70e2bfd0e9f3b021a",
            size = 131_652_445,
        ),
        ModelFile(
            name = "tokens.txt",
            url = "$NEMO_ES_REPO/tokens.txt",
            sha256 = "6191b4853e3654f053c42f6fd184ca53b402987aefdd6ff6baa68034d803ee85",
            size = 10_871,
        ),
    )

}

/**
 * Descarga el modelo a una carpeta privada de la app y verifica cada archivo con SHA-256
 * mientras se descarga. Solo cuando todo está correcto se marca como instalado.
 */
class HttpModelStore(
    private val directory: File,
    private val files: List<ModelFile>,
) : ModelStore {

    private val marker = File(directory, ".instalado")

    override val modelDir: String get() = directory.absolutePath

    override val totalBytes: Long get() = files.sumOf { it.size }

    override val isInstalled: Boolean
        get() = marker.isFile && files.all { File(directory, it.name).length() == it.size }

    override suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        marker.delete()
        val total = files.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        var lastReported = -1

        fun report(bytes: Long) {
            done += bytes
            val percent = (done * 100 / total).toInt()
            if (percent != lastReported) {
                lastReported = percent
                onProgress(done.toFloat() / total)
            }
        }

        for (file in files) {
            val target = File(directory, file.name)
            if (target.length() == file.size && sha256Of(target) == file.sha256) {
                report(file.size)
                continue
            }
            val partial = File(directory, file.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URI(file.url).toURL().openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            connection.getInputStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        report(read.toLong())
                    }
                }
            }
            val actual = digest.digest().toHex()
            if (actual != file.sha256) {
                partial.delete()
                throw IOException("El archivo ${file.name} llegó dañado o alterado (SHA-256 distinto)")
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        marker.writeText("ok")
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
