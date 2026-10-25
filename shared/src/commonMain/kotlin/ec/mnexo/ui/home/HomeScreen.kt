package ec.mnexo.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ec.mnexo.conversation.ConversationTurn
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.sayHello
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.BigSecondaryButton
import ec.mnexo.ui.components.ConversationList
import ec.mnexo.ui.components.ListenControls
import ec.mnexo.ui.components.ListenPanel
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle
import ec.mnexo.ui.components.TypeToAssistant
import ec.mnexo.ui.components.VoiceControls
import ec.mnexo.ui.components.VoiceStatus
import ec.mnexo.voice.SpeakerState

/** Pantalla principal: una acción destacada ("háblame"), la alternativa de escribir y la conversación. */
@Composable
fun HomeScreen(
    settings: UserSettings,
    voice: VoiceControls,
    listen: ListenControls,
    turns: List<ConversationTurn>,
    onSendText: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val speaking = voice.state == SpeakerState.Speaking

    ScreenColumn {
        ScreenTitle(sayHello(settings.userName))
        Text(
            text = "Soy ${settings.assistantName}. ¿Qué necesitas hacer?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        ListenPanel(listen)
        TypeToAssistant(onSend = onSendText)

        VoiceStatus(state = voice.state, onOpenVoiceSetup = voice.onOpenVoiceSetup)
        if (speaking) {
            BigButton("Callar", onClick = voice.onStop)
        }

        ConversationList(turns = turns, assistantName = settings.assistantName)

        BigSecondaryButton("Repetir", onClick = voice.onRepeat, enabled = voice.canRepeat && !speaking)
        BigSecondaryButton(
            text = if (settings.speechRate.isSlowest) "Ya hablo lo más despacio posible" else "Repetir más despacio",
            onClick = voice.onRepeatSlower,
            enabled = voice.canRepeat && !settings.speechRate.isSlowest,
        )
        BigSecondaryButton("Ajustes", onClick = onOpenSettings)
    }
}
