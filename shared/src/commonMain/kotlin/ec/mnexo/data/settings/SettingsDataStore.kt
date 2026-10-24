package ec.mnexo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

const val SETTINGS_FILE_NAME = "mnexo_settings.preferences_pb"

/**
 * Punto de entrada para Android y Windows: solo necesitan decir dónde guardar el archivo.
 * Así no dependen de DataStore y podríamos cambiar el almacenamiento sin tocarlas.
 */
fun createSettingsRepository(filePath: String): SettingsRepository =
    DataStoreSettingsRepository(createSettingsDataStore(filePath))

/**
 * Crea el almacén de preferencias en la ruta indicada.
 * Android le pasa una ruta dentro de la carpeta privada de la app; Windows, una carpeta en %APPDATA%.
 *
 * IMPORTANTE: debe existir UNA sola instancia por archivo mientras la app esté abierta.
 */
fun createSettingsDataStore(
    filePath: String,
    scope: CoroutineScope? = null,
): DataStore<Preferences> {
    // Si el archivo se daña, se reemplaza por uno vacío en lugar de cerrar la app.
    val corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
    val produceFile = { filePath.toPath() }
    return if (scope == null) {
        PreferenceDataStoreFactory.createWithPath(corruptionHandler = corruptionHandler, produceFile = produceFile)
    } else {
        PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = corruptionHandler,
            scope = scope,
            produceFile = produceFile,
        )
    }
}
