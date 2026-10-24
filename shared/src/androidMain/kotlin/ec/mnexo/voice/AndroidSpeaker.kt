package ec.mnexo.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Voz de Android con el motor TextToSpeech del sistema (en tu Redmi: "Google Speech Services").
 * Funciona sin internet una vez descargada la voz en español.
 */
class AndroidSpeaker(context: Context) : Speaker {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<SpeakerState>(SpeakerState.Preparing)
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    @Volatile private var engineStarted = false
    @Volatile private var languageReady = false

    /** Lo que se pidió decir antes de que el motor terminara de iniciar. */
    @Volatile private var pending: Pair<String, SpeechRate>? = null

    private val tts: TextToSpeech = TextToSpeech(appContext) { status -> onEngineStarted(status) }

    private fun onEngineStarted(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "No se pudo iniciar TextToSpeech (status=$status)")
            _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
            return
        }
        engineStarted = true
        tts.setOnUtteranceProgressListener(progressListener)
        if (configureSpanish()) {
            _state.value = SpeakerState.Idle
            pending?.let { (text, rate) ->
                pending = null
                speak(text, rate)
            }
        }
    }

    /** Elige la primera variante de español disponible. Devuelve false si no hay ninguna instalada. */
    private fun configureSpanish(): Boolean {
        val locale = PREFERRED_LOCALES.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        val result = if (locale != null) tts.setLanguage(locale) else TextToSpeech.LANG_NOT_SUPPORTED
        languageReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!languageReady) _state.value = SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE)
        return languageReady
    }

    override fun speak(text: String, rate: SpeechRate) {
        if (!engineStarted) {
            pending = text to rate
            return
        }
        // Si la persona acaba de instalar la voz, se vuelve a comprobar aquí.
        if (!languageReady && !configureSpanish()) return

        tts.setSpeechRate(rate.multiplier)
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
        }
    }

    override fun stop() {
        pending = null
        if (engineStarted) tts.stop()
        if (_state.value == SpeakerState.Speaking) _state.value = SpeakerState.Idle
    }

    override fun openVoiceSetup(): Boolean {
        val installVoice = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val ttsSettings = Intent("com.android.settings.TTS_SETTINGS")
        return listOf(installVoice, ttsSettings).any { intent ->
            try {
                appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _state.value = SpeakerState.Speaking
        }

        override fun onDone(utteranceId: String?) {
            _state.value = SpeakerState.Idle
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            _state.value = SpeakerState.Idle
        }

        @Deprecated("Requerido por versiones antiguas de la API")
        override fun onError(utteranceId: String?) {
            _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "Error al hablar (código $errorCode)")
            _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
        }
    }

    private companion object {
        const val TAG = "MnexoVoz"
        const val UTTERANCE_ID = "mnexo"

        // Primero variantes latinoamericanas (más cercanas al habla de Ecuador), luego España y genérico.
        val PREFERRED_LOCALES = listOf("es-EC", "es-US", "es-MX", "es-419", "es-ES", "es")
            .map(Locale::forLanguageTag)
    }
}
