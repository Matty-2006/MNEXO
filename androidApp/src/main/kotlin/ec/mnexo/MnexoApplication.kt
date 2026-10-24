package ec.mnexo

import android.app.Application
import ec.mnexo.data.settings.SETTINGS_FILE_NAME
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.createSettingsRepository

/**
 * Vive mientras la app está abierta. Aquí se crea el almacén de preferencias una sola vez:
 * si se creara en la pantalla, Android lo duplicaría al girar el celular.
 */
class MnexoApplication : Application() {
    val settingsRepository: SettingsRepository by lazy {
        // filesDir es la carpeta privada de la app: otras apps no pueden leerla.
        createSettingsRepository(filesDir.resolve(SETTINGS_FILE_NAME).absolutePath)
    }
}
