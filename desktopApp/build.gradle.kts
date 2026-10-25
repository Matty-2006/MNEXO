import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Archivos de la voz propia (descargados y verificados por las tareas del build.gradle.kts raíz).
val voiceCache = rootProject.layout.projectDirectory.dir(".voice-cache")

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // Motor de voz sin internet sherpa-onnx (Apache-2.0): API Java + librerías nativas de Windows x64
    implementation(files(voiceCache.file("downloads/sherpa-onnx-jvm-1.13.8.jar")))
    runtimeOnly(files(voiceCache.file("downloads/sherpa-onnx-native-lib-win-x64-1.13.8.jar")))

    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "ec.mnexo.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "MNEXO"
            packageVersion = "1.0.0"
            // La voz va en common/voice/es_MX-claude/; la app la encuentra con la propiedad
            // "compose.application.resources.dir" (tanto al ejecutar desde Gradle como instalada).
            appResourcesRootDir.set(voiceCache.dir("app-resources"))
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(":downloadSherpaJvm", ":downloadSherpaWindows")
}
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(":extractVoiceModel")
}
tasks.withType<Test>().configureEach {
    dependsOn(":extractVoiceModel")
    // La prueba de la voz propia necesita saber dónde está el modelo.
    systemProperty("mnexo.voice.dir", voiceCache.dir("app-resources/common/voice/es_MX-claude").asFile.absolutePath)
}
