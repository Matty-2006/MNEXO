import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// ---------------------------------------------------------------------------------------------
// Voz propia de MNEXO (sherpa-onnx + voz Piper es_MX "claude").
// Los archivos son grandes, así que no se guardan en Git: se descargan al compilar en
// .voice-cache/ y se comprueba su huella SHA-256. Si el archivo fue alterado, la compilación falla.
// ---------------------------------------------------------------------------------------------

/** Descarga un archivo y verifica su SHA-256. Si ya existe y la huella coincide, no hace nada. */
abstract class DownloadVerified : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputFile abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val target = destination.get().asFile
        val expected = sha256.get().lowercase()
        if (target.isFile && sha256Of(target) == expected) return

        target.parentFile.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        java.net.URI(url.get()).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        val actual = sha256Of(partial)
        if (actual != expected) {
            partial.delete()
            throw GradleException("La huella SHA-256 de ${url.get()} no coincide: se esperaba $expected y llegó $actual")
        }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val sherpaVersion = "1.13.8"
val sherpaReleases = "https://github.com/k2-fsa/sherpa-onnx/releases/download"
val voiceDownloads = layout.projectDirectory.dir(".voice-cache/downloads")

val downloadSherpaAndroid by tasks.registering(DownloadVerified::class) {
    url = "$sherpaReleases/v$sherpaVersion/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
    sha256 = "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"
    destination = voiceDownloads.file("sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar")
}

val downloadSherpaJvm by tasks.registering(DownloadVerified::class) {
    url = "$sherpaReleases/v$sherpaVersion/sherpa-onnx-jvm-$sherpaVersion.jar"
    sha256 = "77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b"
    destination = voiceDownloads.file("sherpa-onnx-jvm-$sherpaVersion.jar")
}

val downloadSherpaWindows by tasks.registering(DownloadVerified::class) {
    url = "$sherpaReleases/v$sherpaVersion/sherpa-onnx-native-lib-win-x64-$sherpaVersion.jar"
    sha256 = "33fbdbd5410e9ba9bdda94aa164ec8f7825bb49246420d8ce9bdd88219d97039"
    destination = voiceDownloads.file("sherpa-onnx-native-lib-win-x64-$sherpaVersion.jar")
}

val downloadVoiceModel by tasks.registering(DownloadVerified::class) {
    url = "$sherpaReleases/tts-models/vits-piper-es_MX-claude-high-int8.tar.bz2"
    sha256 = "0f9fc9c07d2e17bdc0f5f33a657addae92085da85704cb86a861e59d32f3bbfa"
    destination = voiceDownloads.file("vits-piper-es_MX-claude-high-int8.tar.bz2")
}

/**
 * Extrae la voz a .voice-cache/app-resources/common/voice/es_MX-claude/.
 * Android la usa como "assets" y Windows como recursos de la app.
 * De los diccionarios de pronunciación solo se deja el español, para ahorrar espacio.
 */
val extractVoiceModel by tasks.registering(Sync::class) {
    dependsOn(downloadVoiceModel)
    from(tarTree(resources.bzip2(downloadVoiceModel.flatMap { it.destination }))) {
        include("*/es_MX-claude-high.onnx", "*/tokens.txt", "*/MODEL_CARD", "*/espeak-ng-data/**")
        exclude { it.name.endsWith("_dict") && it.name != "es_dict" }
        eachFile {
            val inner = relativePath.segments.drop(1).toMutableList()
            if (inner.lastOrNull() == "es_MX-claude-high.onnx") inner[inner.lastIndex] = "model.onnx"
            relativePath = RelativePath(true, *(listOf("voice", "es_MX-claude") + inner).toTypedArray())
        }
    }
    includeEmptyDirs = false
    into(layout.projectDirectory.dir(".voice-cache/app-resources/common"))
}

tasks.register("prepareVoice") {
    group = "mnexo"
    description = "Descarga y prepara la voz propia de MNEXO (motor sherpa-onnx y voz es_MX)."
    dependsOn(downloadSherpaAndroid, downloadSherpaJvm, downloadSherpaWindows, extractVoiceModel)
}
