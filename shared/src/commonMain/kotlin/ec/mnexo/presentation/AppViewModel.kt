package ec.mnexo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.voice.Phrases
import ec.mnexo.voice.SpeakerState
import ec.mnexo.voice.Speaker
import ec.mnexo.voice.SpeechRate
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
        speaker.stop()
        _lastSpoken.value = null
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
        _lastSpoken.value?.let { speaker.speak(it, settings.speechRate) }
    }

    /** "Más despacio": baja un nivel la velocidad, la deja guardada y repite la última frase. */
    fun repeatSlower() {
        val settings = currentSettings() ?: return
        val slower = settings.speechRate.slower()
        if (slower != settings.speechRate) save(settings.copy(speechRate = slower))
        _lastSpoken.value?.let { speaker.speak(it, slower) }
    }

    /** Muestra cómo suena una velocidad sin guardarla todavía. */
    fun previewRate(rate: SpeechRate) {
        say(Phrases.rateSample(rate), rate)
    }

    fun stopSpeaking() = speaker.stop()

    fun openVoiceSetup(): Boolean = speaker.openVoiceSetup()

    override fun onCleared() {
        speaker.stop()
    }

    private fun say(text: String, rate: SpeechRate) {
        _lastSpoken.value = text
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
