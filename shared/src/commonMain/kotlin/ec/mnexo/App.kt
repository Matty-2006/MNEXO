package ec.mnexo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.listening.Listener
import ec.mnexo.platform.SystemBackHandler
import ec.mnexo.platform.rememberMicrophonePermission
import ec.mnexo.ui.components.ListenControls
import ec.mnexo.presentation.AppUiState
import ec.mnexo.presentation.AppViewModel
import ec.mnexo.ui.components.VoiceControls
import ec.mnexo.ui.home.HomeScreen
import ec.mnexo.ui.onboarding.OnboardingScreen
import ec.mnexo.ui.settings.SettingsScreen
import ec.mnexo.ui.theme.MnexoTheme
import ec.mnexo.voice.Speaker

/**
 * Raíz de la app, compartida por Android y Windows.
 * Cada plataforma le entrega dónde guardar los datos y su motor de voz.
 */
@Composable
fun App(settingsRepository: SettingsRepository, speaker: Speaker, listener: Listener) {
    val viewModel = viewModel { AppViewModel(settingsRepository, speaker, listener) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val speakerState by viewModel.speakerState.collectAsStateWithLifecycle()
    val lastSpoken by viewModel.lastSpoken.collectAsStateWithLifecycle()
    val listenerState by viewModel.listenerState.collectAsStateWithLifecycle()
    val heard by viewModel.heard.collectAsStateWithLifecycle()
    val microphone = rememberMicrophonePermission()

    val listen = ListenControls(
        state = listenerState,
        heard = heard,
        modelSizeMb = viewModel.listenModelSizeMb,
        onTalk = {
            // El permiso se pide justo cuando hace falta, nunca antes.
            microphone.request { granted ->
                if (granted) viewModel.startListening() else viewModel.onMicrophonePermissionDenied()
            }
        },
        onStopListening = viewModel::stopListening,
        onDownload = viewModel::downloadListenModel,
        onCancelDownload = viewModel::cancelListenModelDownload,
        onOpenMicrophoneSettings = microphone::openAppSettings,
    )

    val voice = VoiceControls(
        state = speakerState,
        canRepeat = lastSpoken != null,
        onSpeakGreeting = viewModel::speakGreeting,
        onStop = viewModel::stopSpeaking,
        onRepeat = viewModel::repeatLast,
        onRepeatSlower = viewModel::repeatSlower,
        onPreviewRate = viewModel::previewRate,
        onPreviewSource = viewModel::previewSource,
        onOpenVoiceSetup = { viewModel.openVoiceSetup() },
    )

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
            voice = voice,
            onFinish = viewModel::completeOnboarding,
        )

        is AppUiState.Ready -> {
            LaunchedEffect(Unit) { viewModel.greetIfNeeded() }
            MainScreens(
                settings = current.settings,
                voice = voice,
                listen = listen,
                onSave = viewModel::updateSettings,
                onDeleteAllData = viewModel::deleteAllData,
            )
        }
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
    voice: VoiceControls,
    listen: ListenControls,
    onSave: (UserSettings) -> Unit,
    onDeleteAllData: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    MnexoTheme(textSize = settings.textSize, highContrast = settings.highContrast) {
        if (showSettings) {
            SystemBackHandler { showSettings = false }
            SettingsScreen(
                settings = settings,
                voice = voice,
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
            HomeScreen(settings = settings, voice = voice, listen = listen, onOpenSettings = { showSettings = true })
        }
    }
}
