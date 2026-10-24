package ec.mnexo

import android.app.Application
import ec.mnexo.data.settings.SETTINGS_FILE_NAME
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.createSettingsRepository
import ec.mnexo.voice.AndroidSpeaker
import ec.mnexo.voice.Speaker

/**
 * Vive mientras la app está abierta. Aquí se crean una sola vez el almacén de preferencias
 * y el motor de voz: si se crearan en la pantalla, Android los duplicaría al girar el celular.
 */
class MnexoApplication : Application() {
    val settingsRepository: SettingsRepository by lazy {
        // filesDir es la carpeta privada de la app: otras apps no pueden leerla.
        createSettingsRepository(filesDir.resolve(SETTINGS_FILE_NAME).absolutePath)
    }

    val speaker: Speaker by lazy { AndroidSpeaker(this) }
}
