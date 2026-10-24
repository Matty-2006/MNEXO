package ec.mnexo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.IOException

/** Dónde y cómo se guardan las preferencias. La interfaz permite cambiar el almacenamiento sin tocar la interfaz gráfica. */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun save(settings: UserSettings)

    /** Borra todos los datos de la persona guardados en este equipo. */
    suspend fun clear()
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        // Si el archivo no se puede leer, la app sigue funcionando con los valores por defecto.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            UserSettings(
                userName = prefs[Keys.USER_NAME].orEmpty(),
                assistantName = prefs[Keys.ASSISTANT_NAME] ?: DEFAULT_ASSISTANT_NAME,
                textSize = TextSize.fromStored(prefs[Keys.TEXT_SIZE]),
                highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
                onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            )
        }

    override suspend fun save(settings: UserSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = sanitizeName(settings.userName)
            prefs[Keys.ASSISTANT_NAME] = sanitizeAssistantName(settings.assistantName)
            prefs[Keys.TEXT_SIZE] = settings.textSize.name
            prefs[Keys.HIGH_CONTRAST] = settings.highContrast
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
