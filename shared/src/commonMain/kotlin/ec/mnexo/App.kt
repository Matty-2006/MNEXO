package ec.mnexo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.platform.SystemBackHandler
import ec.mnexo.presentation.AppUiState
import ec.mnexo.presentation.AppViewModel
import ec.mnexo.ui.home.HomeScreen
import ec.mnexo.ui.onboarding.OnboardingScreen
import ec.mnexo.ui.settings.SettingsScreen
import ec.mnexo.ui.theme.MnexoTheme

/** Raíz de la app, compartida por Android y Windows. Cada plataforma le entrega dónde guardar los datos. */
@Composable
fun App(settingsRepository: SettingsRepository) {
    val viewModel = viewModel { AppViewModel(settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    when (val current = state) {
        AppUiState.Loading -> {
            // Fondo vacío mientras se leen las preferencias, para no mostrar un parpadeo.
            val defaults = UserSettings()
            MnexoTheme(textSize = defaults.textSize, highContrast = defaults.highContrast) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }

        is AppUiState.Onboarding -> OnboardingScreen(
            initial = current.settings,
            onFinish = viewModel::completeOnboarding,
        )

        is AppUiState.Ready -> MainScreens(
            settings = current.settings,
            onSave = viewModel::updateSettings,
            onDeleteAllData = viewModel::deleteAllData,
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("Entendido", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

@Composable
private fun MainScreens(
    settings: UserSettings,
    onSave: (UserSettings) -> Unit,
    onDeleteAllData: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    MnexoTheme(textSize = settings.textSize, highContrast = settings.highContrast) {
        if (showSettings) {
            SystemBackHandler { showSettings = false }
            SettingsScreen(
                settings = settings,
                onSave = {
                    onSave(it)
                    showSettings = false
                },
                onBack = { showSettings = false },
                onDeleteAllData = {
                    onDeleteAllData()
                    showSettings = false
                },
            )
        } else {
            HomeScreen(settings = settings, onOpenSettings = { showSettings = true })
        }
    }
}
