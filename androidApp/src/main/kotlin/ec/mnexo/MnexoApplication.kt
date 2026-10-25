package ec.mnexo

import android.app.Application
import ec.mnexo.conversation.ConversationStore
import ec.mnexo.data.db.createConversationStore
import ec.mnexo.data.settings.SETTINGS_FILE_NAME
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.createSettingsRepository
import ec.mnexo.listening.HttpModelStore
import ec.mnexo.listening.Listener
import ec.mnexo.listening.OfflineListener
import ec.mnexo.listening.SherpaAndroidListenEngine
import ec.mnexo.listening.SpeechModels
import ec.mnexo.voice.AndroidSpeaker
import ec.mnexo.voice.FallbackSpeaker
import ec.mnexo.voice.SherpaAndroidSpeaker
import ec.mnexo.voice.Speaker
import ec.mnexo.voiceprint.KeystoreVoiceProfileStore
import ec.mnexo.voiceprint.VoiceProfileStore

/**
 * Vive mientras la app está abierta. Aquí se crean una sola vez el almacén de preferencias
 * y el motor de voz: si se crearan en la pantalla, Android los duplicaría al girar el celular.
 */
class MnexoApplication : Application() {
    val settingsRepository: SettingsRepository by lazy {
        // filesDir es la carpeta privada de la app: otras apps no pueden leerla.
        createSettingsRepository(filesDir.resolve(SETTINGS_FILE_NAME).absolutePath)
    }

    /** Voz del celular si tiene español; si no, la voz propia de MNEXO. */
    val speaker: Speaker by lazy {
        FallbackSpeaker(system = AndroidSpeaker(this), own = SherpaAndroidSpeaker(this))
    }

    /** Oído de MNEXO: el modelo se descarga una vez a la carpeta privada de la app. */
    val listener: Listener by lazy {
        OfflineListener(
            store = HttpModelStore(filesDir.resolve("models/${SpeechModels.EAR_DIR_NAME}"), SpeechModels.EAR_FILES),
            engine = SherpaAndroidListenEngine(this),
        )
    }

    /** Huella de voz del dueño, cifrada con el Android Keystore. */
    val voiceProfiles: VoiceProfileStore by lazy { KeystoreVoiceProfileStore(this) }

    /** Historial de conversación en la base de datos local (Room / SQLite). */
    val conversations: ConversationStore by lazy { createConversationStore(this) }
}
