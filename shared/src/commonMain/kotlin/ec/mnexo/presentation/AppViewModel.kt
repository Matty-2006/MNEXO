package ec.mnexo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
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
class AppViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<AppUiState> = repository.settings
        .map { if (it.onboardingCompleted) AppUiState.Ready(it) else AppUiState.Onboarding(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState.Loading)

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Mensaje para la persona si algo no se pudo guardar; null si todo va bien. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun completeOnboarding(settings: UserSettings) = save(settings.copy(onboardingCompleted = true))

    fun updateSettings(settings: UserSettings) = save(settings)

    fun deleteAllData() {
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
