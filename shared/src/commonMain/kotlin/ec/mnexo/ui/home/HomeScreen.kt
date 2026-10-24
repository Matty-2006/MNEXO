package ec.mnexo.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.sayHello
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.BigSecondaryButton
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle
import ec.mnexo.ui.components.VoiceControls
import ec.mnexo.ui.components.VoiceStatus
import ec.mnexo.voice.SpeakerState

@Composable
fun HomeScreen(settings: UserSettings, voice: VoiceControls, onOpenSettings: () -> Unit) {
    val speaking = voice.state == SpeakerState.Speaking

    ScreenColumn {
        ScreenTitle(sayHello(settings.userName))
        Text(
            text = "Soy ${settings.assistantName}. ¿Qué necesitas hacer?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        VoiceStatus(state = voice.state, onOpenVoiceSetup = voice.onOpenVoiceSetup)

        if (speaking) {
            BigButton("Detener", onClick = voice.onStop)
        } else {
            BigButton("Escuchar saludo", onClick = voice.onSpeakGreeting)
        }
        BigSecondaryButton("Repetir", onClick = voice.onRepeat, enabled = voice.canRepeat && !speaking)
        BigSecondaryButton(
            text = if (settings.speechRate.isSlowest) "Ya hablo lo más despacio posible" else "Repetir más despacio",
            onClick = voice.onRepeatSlower,
            enabled = voice.canRepeat && !settings.speechRate.isSlowest,
        )

        // Aviso honesto: todavía no escucha, así que no hay botón de micrófono.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "Muy pronto podrás hablarme y pedirme tareas. Por ahora puedo hablarte y puedes cambiar cómo me ves en Ajustes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        BigButton("Ajustes", onClick = onOpenSettings)
    }
}
