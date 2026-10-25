import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// Archivos de la voz propia (descargados y verificados por las tareas del build.gradle.kts raíz).
val voiceCache = rootProject.layout.projectDirectory.dir(".voice-cache")

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Motor de voz sin internet sherpa-onnx (Apache-2.0)
    implementation(files(voiceCache.file("downloads/sherpa-onnx-static-link-onnxruntime-1.13.8.aar")))
}

android {
    namespace = "ec.mnexo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ec.mnexo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        ndk {
            // Solo procesadores ARM: los de los celulares reales (64 y 32 bits, estos últimos en
            // celulares económicos). Así la app pesa unos 30 MB menos.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    sourceSets {
        // La voz va como "assets" en voice/es_MX-claude/
        getByName("main").assets.directories.add(voiceCache.dir("app-resources/common").asFile.path)
    }
    androidResources {
        // El modelo de voz ya está comprimido internamente; comprimirlo otra vez solo haría más lenta la carga.
        noCompress += "onnx"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.named("preBuild") {
    dependsOn(":downloadSherpaAndroid", ":extractVoiceModel", ":prepareVad")
}
