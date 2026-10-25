package ec.mnexo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.listening.ListenProblem
import ec.mnexo.listening.ListenResult
import ec.mnexo.listening.Listener
import ec.mnexo.listening.ListenerState
import kotlinx.coroutines.Job
import ec.mnexo.voice.Phrases
import ec.mnexo.voice.SpeakerState
import ec.mnexo.voice.Speaker
import ec.mnexo.voice.SpeechRate
import ec.mnexo.voice.VoiceSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.IOException

sealed interface AppUiState {
    /** Leyendo las preferencias guardadas (dura una fracción de segundo). */
    data object Loading : AppUiState

    /** Primera vez: falta la configuración inicial. */
    data class Onboarding(val settings: UserSettings) : AppUiState

    data class Ready(val settings: UserSettings) : AppUiState
}

/**
 * Lógica de la app separada de las pantallas (patrón MVVM).
 * Sobrevive cuando Android recrea la pantalla, por ejemplo al girar el celular.
 */
class AppViewModel(
    private val repository: SettingsRepository,
    private val speaker: Speaker,
    private val listener: Listener,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = repository.settings
        .map { if (it.onboardingCompleted) AppUiState.Ready(it) else AppUiState.Onboarding(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState.Loading)

    val speakerState: StateFlow<SpeakerState> = speaker.state

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Mensaje para la persona si algo no se pudo guardar; null si todo va bien. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastSpoken = MutableStateFlow<String?>(null)

    /** Última frase dicha, para poder repetirla. */
    val lastSpoken: StateFlow<String?> = _lastSpoken.asStateFlow()

    /** El saludo automático se dice una sola vez por apertura de la app, no cada vez que se gira la pantalla. */
    private var greetedThisSession = false

    fun completeOnboarding(settings: UserSettings) = save(settings.copy(onboardingCompleted = true))

    fun updateSettings(settings: UserSettings) = save(settings)

    fun deleteAllData() {
        stopListening()
        speaker.stop()
        _lastSpoken.value = null
        _heard.value = null
        viewModelScope.launch {
            try {
                repository.clear()
                _errorMessage.value = null
            } catch (e: IOException) {
                _errorMessage.value = "No pude borrar tus datos. Inténtalo de nuevo."
            }
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    // ---- Voz ----

    /** Llamado al mostrar la pantalla principal: saluda si la persona lo tiene activado. */
    fun greetIfNeeded() {
        if (greetedThisSession) return
        val settings = currentSettings() ?: return
        greetedThisSession = true
        if (settings.speakOnOpen) say(Phrases.greeting(settings), settings.speechRate)
    }

    fun speakGreeting() {
        val settings = currentSettings() ?: return
        say(Phrases.greeting(settings), settings.speechRate)
    }

    fun repeatLast() {
        val settings = currentSettings() ?: return
        _lastSpoken.value?.let { say(it, settings.speechRate) }
    }

    /** "Más despacio": baja un nivel la velocidad, la deja guardada y repite la última frase. */
    fun repeatSlower() {
        val settings = currentSettings() ?: return
        val slower = settings.speechRate.slower()
        if (slower != settings.speechRate) save(settings.copy(speechRate = slower))
        _lastSpoken.value?.let { say(it, slower) }
    }

    /** Muestra cómo suena una velocidad sin guardarla todavía. */
    fun previewRate(rate: SpeechRate) {
        say(Phrases.rateSample(rate), rate)
    }

    /** Muestra cómo suena una voz sin guardarla todavía. */
    fun previewSource(source: VoiceSource) {
        val settings = currentSettings() ?: return
        say(Phrases.sourceSample(source), settings.speechRate, source)
    }

    fun stopSpeaking() = speaker.stop()

    // ---- Oído ----

    val listenerState: StateFlow<ListenerState> = listener.state

    val listenModelSizeMb: Long get() = listener.modelSizeMb

    private val _heard = MutableStateFlow<String?>(null)

    /** Lo último que el asistente entendió, para mostrarlo en pantalla. */
    val heard: StateFlow<String?> = _heard.asStateFlow()

    private var listenJob: Job? = null

    fun downloadListenModel() = listener.downloadModel()

    fun cancelListenModelDownload() = listener.cancelDownload()

    /** Escucha una frase y confirma en voz alta lo que entendió. */
    fun startListening() {
        if (listenJob?.isActive == true) return
        val settings = currentSettings() ?: return
        // Se calla antes de escuchar, para no oírse a sí mismo.
        speaker.stop()
        listenJob = viewModelScope.launch {
            when (val result = listener.listen()) {
                is ListenResult.Heard -> {
                    _heard.value = result.text
                    say(Phrases.heard(result.text), settings.speechRate)
                }
                ListenResult.NothingHeard -> say(Phrases.NOTHING_HEARD, settings.speechRate)
                is ListenResult.Failed -> say(Phrases.listenProblemMessage(result.problem), settings.speechRate)
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    /** Si la persona negó el permiso del micrófono, se le explica cómo darlo. */
    fun onMicrophonePermissionDenied() {
        val settings = currentSettings() ?: return
        say(Phrases.listenProblemMessage(ListenProblem.PERMISSION_DENIED), settings.speechRate)
    }

    fun openVoiceSetup(): Boolean = speaker.openVoiceSetup()

    override fun onCleared() {
        listenJob?.cancel()
        speaker.stop()
    }

    /** Todo lo que dice el asistente pasa por aquí: aplica la voz elegida y recuerda la frase. */
    private fun say(text: String, rate: SpeechRate, source: VoiceSource? = null) {
        _lastSpoken.value = text
        (source ?: currentSettings()?.voiceSource)?.let(speaker::setPreferredSource)
        speaker.speak(text, rate)
    }

    private fun currentSettings(): UserSettings? = when (val state = uiState.value) {
        is AppUiState.Ready -> state.settings
        is AppUiState.Onboarding -> state.settings
        AppUiState.Loading -> null
    }

    private fun save(settings: UserSettings) {
        viewModelScope.launch {
            try {
                repository.save(settings)
                _errorMessage.value = null
            } catch (e: IOException) {
                _errorMessage.value = "No pude guardar los cambios. Inténtalo de nuevo."
            }
        }
    }
}
