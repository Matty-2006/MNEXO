package ec.mnexo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ec.mnexo.data.settings.SETTINGS_FILE_NAME
import ec.mnexo.data.settings.createSettingsRepository
import java.io.File

fun main() {
    // En Windows los datos van a %APPDATA%\MNEXO (por ejemplo C:\Users\Mateo\AppData\Roaming\MNEXO).
    val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MNEXO").apply { mkdirs() }
    val settingsRepository = createSettingsRepository(File(dataDir, SETTINGS_FILE_NAME).absolutePath)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MNEXO",
            // Ventana amplia desde el inicio para que el texto grande se lea sin redimensionar.
            state = rememberWindowState(width = 900.dp, height = 760.dp),
        ) {
            App(settingsRepository)
        }
    }
}
