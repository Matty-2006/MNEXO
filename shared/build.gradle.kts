import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    // Esquemas de la base de datos (versionados en Git): documentan el modelo y permiten migraciones.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Room usa "expect object" para construir la base de datos en cada plataforma.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    
    android {
       namespace = "ec.mnexo.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        // Código que usa Java puro (descargas, SHA-256) y sirve igual en Android y en escritorio.
        getByName("androidMain").kotlin.srcDir("src/jvmShared/kotlin")
        getByName("jvmMain").kotlin.srcDir("src/jvmShared/kotlin")

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // BackHandler: botón "atrás" del sistema Android
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Preferencias guardadas en el propio equipo (Android y Windows)
            implementation(libs.androidx.datastore.preferences.core)
            // Base de datos local (historial de conversación; luego métricas, encuestas...)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // Fecha y hora local ("¿qué hora es?")
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    // Room genera el código de la base de datos para cada plataforma.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}