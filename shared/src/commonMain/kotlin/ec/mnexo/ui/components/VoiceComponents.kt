package ec.mnexo.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ec.mnexo.voice.Phrases
import ec.mnexo.voice.SpeakerState
import ec.mnexo.voice.SpeechRate
import ec.mnexo.voice.VoiceSource
import ec.mnexo.voice.VoiceProblem

/** Todo lo que las pantallas necesitan para usar la voz, agrupado para no pasar diez parámetros sueltos. */
class VoiceControls(
    val state: SpeakerState,
    /** Hay una frase anterior que se puede repetir. */
    val canRepeat: Boolean,
    val onSpeakGreeting: () -> Unit,
    val onStop: () -> Unit,
    val onRepeat: () -> Unit,
    val onRepeatSlower: () -> Unit,
    val onPreviewRate: (SpeechRate) -> Unit,
    val onPreviewSource: (VoiceSource) -> Unit,
    val onOpenVoiceSetup: () -> Unit,
)

/**
 * Indica con texto lo que hace la voz ("Hablando…") o explica un problema.
 * Es una "región viva": TalkBack la lee sola cuando cambia.
 */
@Composable
fun VoiceStatus(state: SpeakerState, onOpenVoiceSetup: () -> Unit) {
    val message = when (state) {
        SpeakerState.Preparing -> "Preparando la voz…"
        SpeakerState.Speaking -> "Hablando…"
        SpeakerState.Idle -> null
        is SpeakerState.Unavailable -> Phrases.problemMessage(state.problem)
    } ?: return

    val isProblem = state is SpeakerState.Unavailable
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isProblem) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isProblem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(20.dp),
        )
    }
    if (state is SpeakerState.Unavailable && state.problem == VoiceProblem.NO_SPANISH_VOICE) {
        BigButton("Instalar voz en español", onClick = onOpenVoiceSetup)
    }
}
