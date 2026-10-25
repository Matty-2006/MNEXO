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
import ec.mnexo.listening.ListenProblem
import ec.mnexo.listening.Listener
import ec.mnexo.listening.ListenerState
import ec.mnexo.platform.SystemBackHandler
import ec.mnexo.platform.rememberMicrophonePermission
import ec.mnexo.presentation.AppUiState
import ec.mnexo.presentation.AppViewModel
import ec.mnexo.ui.components.ListenControls
import ec.mnexo.ui.components.VoiceControls
import ec.mnexo.ui.home.HomeScreen
import ec.mnexo.ui.onboarding.OnboardingScreen
import ec.mnexo.ui.settings.SettingsScreen
import ec.mnexo.ui.theme.MnexoTheme
import ec.mnexo.ui.voiceprint.EnrollmentScreen
import ec.mnexo.voice.Speaker
import ec.mnexo.voiceprint.VoiceProfileStore

/**
 * Raíz de la app, compartida por Android y Windows.
 * Cada plataforma le entrega dónde guardar los datos, su voz, su oído y dónde guardar la huella de voz.
 */
@Composable
fun App(
    settingsRepository: SettingsRepository,
    speaker: Speaker,
    listener: Listener,
    voiceProfiles: VoiceProfileStore,
) {
    val viewModel = viewModel { AppViewModel(settingsRepository, speaker, listener, voiceProfiles) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val speakerState by viewModel.speakerState.collectAsStateWithLifecycle()
    val lastSpoken by viewModel.lastSpoken.collectAsStateWithLifecycle()
    val listenerState by viewModel.listenerState.collectAsStateWithLifecycle()
    val heard by viewModel.heard.collectAsStateWithLifecycle()
    val hasVoiceProfile by viewModel.hasVoiceProfile.collectAsStateWithLifecycle()
    val pendingUnknown by viewModel.pendingUnknown.collectAsStateWithLifecycle()
    val enrollment by viewModel.enrollment.collectAsStateWithLifecycle()
    val microphone = rememberMicrophonePermission()

    /** Pide el permiso del micrófono justo cuando hace falta, nunca antes. */
    fun withMicrophone(action: () -> Unit) = microphone.request { granted ->
        if (granted) action() else viewModel.onMicrophonePermissionDenied()
    }

    val earInstalled = listenerState != ListenerState.NeedsModel &&
        listenerState !is ListenerState.Downloading &&
        listenerState != ListenerState.Unavailable(ListenProblem.DOWNLOAD_FAILED)

    fun listenControls(settings: UserSettings) = ListenControls(
        state = listenerState,
        heard = heard,
        modelSizeMb = viewModel.listenModelSizeMb,
        onTalk = { withMicrophone(viewModel::startListening) },
        onStopListening = viewModel::stopListening,
        onDownload = viewModel::downloadListenModel,
        onCancelDownload = viewModel::cancelListenModelDownload,
        onOpenMicrophoneSettings = microphone::openAppSettings,
        suggestVoiceRegistration = earInstalled && !hasVoiceProfile && settings.ownerVoiceOnly,
        onRegisterVoice = viewModel::startEnrollment,
        pendingUnknown = pendingUnknown,
        onConfirmOwner = viewModel::confirmUnknownSpeakerIsOwner,
        onIgnoreUnknown = viewModel::ignoreUnknownSpeaker,
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
            val settings = current.settings
            MnexoTheme(textSize = settings.textSize, highContrast = settings.highContrast) {
                val enrollmentState = enrollment
                if (enrollmentState != null) {
                    EnrollmentScreen(
                        state = enrollmentState,
                        listenerState = listenerState,
                        onRecord = { withMicrophone(viewModel::recordEnrollmentPhrase) },
                        onHearPhrase = viewModel::speakEnrollmentPhrase,
                        onRestart = viewModel::startEnrollment,
                        onClose = viewModel::closeEnrollment,
                        modelSizeMb = viewModel.listenModelSizeMb,
                        onDownload = viewModel::downloadListenModel,
                    )
                } else {
                    MainScreens(
                        settings = settings,
                        voice = voice,
                        listen = listenControls(settings),
                        hasVoiceProfile = hasVoiceProfile,
                        onSave = viewModel::updateSettings,
                        onDeleteAllData = viewModel::deleteAllData,
                        onRegisterVoice = viewModel::startEnrollment,
                        onDeleteVoiceProfile = viewModel::deleteVoiceProfile,
                    )
                }
            }
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
    hasVoiceProfile: Boolean,
    onSave: (UserSettings) -> Unit,
    onDeleteAllData: () -> Unit,
    onRegisterVoice: () -> Unit,
    onDeleteVoiceProfile: () -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
            hasVoiceProfile = hasVoiceProfile,
            onRegisterVoice = onRegisterVoice,
            onDeleteVoiceProfile = onDeleteVoiceProfile,
        )
    } else {
        HomeScreen(settings = settings, voice = voice, listen = listen, onOpenSettings = { showSettings = true })
    }
}
