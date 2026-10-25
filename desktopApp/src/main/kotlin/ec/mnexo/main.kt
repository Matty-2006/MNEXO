package ec.mnexo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ec.mnexo.data.settings.SETTINGS_FILE_NAME
import ec.mnexo.data.settings.createSettingsRepository
import ec.mnexo.listening.HttpModelStore
import ec.mnexo.listening.OfflineListener
import ec.mnexo.listening.SherpaDesktopListenEngine
import ec.mnexo.listening.SpeechModels
import ec.mnexo.voice.FallbackSpeaker
import ec.mnexo.voice.SherpaDesktopSpeaker
import ec.mnexo.voice.WindowsSpeaker
import java.io.File

fun main() {
    // En Windows los datos van a %APPDATA%\MNEXO (por ejemplo C:\Users\Mateo\AppData\Roaming\MNEXO).
    val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MNEXO").apply { mkdirs() }
    val settingsRepository = createSettingsRepository(File(dataDir, SETTINGS_FILE_NAME).absolutePath)
    // Voz de Windows si tiene español; si no, la voz propia de MNEXO.
    val speaker = FallbackSpeaker(
        system = WindowsSpeaker(),
        own = SherpaDesktopSpeaker(SherpaDesktopSpeaker.findVoiceDir()),
    )
    // Oído de MNEXO: el modelo se descarga una vez a %APPDATA%\MNEXO\models.
    val listener = OfflineListener(
        store = HttpModelStore(File(dataDir, "models/${SpeechModels.NEMO_ES_DIR_NAME}"), SpeechModels.NEMO_ES_CTC),
        engine = SherpaDesktopListenEngine(SherpaDesktopListenEngine.findVadFile()),
    )

    application {
        Window(
            onCloseRequest = {
                speaker.stop()
                exitApplication()
            },
            title = "MNEXO",
            // Ventana amplia desde el inicio para que el texto grande se lea sin redimensionar.
            state = rememberWindowState(width = 900.dp, height = 760.dp),
        ) {
            App(settingsRepository = settingsRepository, speaker = speaker, listener = listener)
        }
    }
}
