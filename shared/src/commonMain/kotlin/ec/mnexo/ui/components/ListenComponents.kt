package ec.mnexo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ec.mnexo.listening.ListenProblem
import ec.mnexo.listening.ListenerState
import ec.mnexo.voice.Phrases

/** Todo lo que la pantalla necesita para usar el oído. */
class ListenControls(
    val state: ListenerState,
    val modelSizeMb: Long,
    val onTalk: () -> Unit,
    val onStopListening: () -> Unit,
    val onDownload: () -> Unit,
    val onCancelDownload: () -> Unit,
    val onOpenMicrophoneSettings: () -> Unit,
    /** Hay que ofrecer registrar la voz (oído listo, sin huella y "solo mi voz" activado). */
    val suggestVoiceRegistration: Boolean,
    val onRegisterVoice: () -> Unit,
    /** Lo que dijo una voz no reconocida, esperando "Sí, soy yo". */
    val pendingUnknown: String?,
    val onConfirmOwner: () -> Unit,
    val onIgnoreUnknown: () -> Unit,
)

/** Panel principal de la pantalla de inicio: el gran botón "Hablar" y su estado. */
@Composable
fun ListenPanel(controls: ListenControls) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (val state = controls.state) {
            ListenerState.NeedsModel -> {
                InfoCard(
                    "Para entenderte sin internet necesito descargar mi oído " +
                        "(${controls.modelSizeMb} MB), una sola vez. Es mejor hacerlo con Wi-Fi.",
                )
                BigButton("Descargar mi oído", onClick = controls.onDownload)
            }

            is ListenerState.Downloading -> {
                val percent = (state.progress * 100).toInt()
                StatusCard("Descargando mi oído… $percent %")
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .semantics { contentDescription = "Descarga al $percent por ciento" },
                )
                BigSecondaryButton("Cancelar descarga", onClick = controls.onCancelDownload)
            }

            ListenerState.Preparing -> StatusCard("Preparándome para escucharte…")

            ListenerState.Listening -> {
                StatusCard("Te escucho… habla cuando quieras.")
                BigButton("Dejar de escuchar", onClick = controls.onStopListening)
            }

            ListenerState.Recognizing -> StatusCard("Pensando en lo que dijiste…")

            ListenerState.Ready -> TalkButton(controls.onTalk)

            is ListenerState.Unavailable -> {
                InfoCard(Phrases.listenProblemMessage(state.problem))
                when (state.problem) {
                    ListenProblem.DOWNLOAD_FAILED -> BigButton("Intentar descargar otra vez", onClick = controls.onDownload)
                    ListenProblem.PERMISSION_DENIED -> {
                        TalkButton(controls.onTalk)
                        BigSecondaryButton("Abrir ajustes del micrófono", onClick = controls.onOpenMicrophoneSettings)
                    }
                    ListenProblem.NO_MICROPHONE, ListenProblem.ENGINE_ERROR -> TalkButton(controls.onTalk)
                }
            }
        }

        controls.pendingUnknown?.let {
            InfoCard("No reconocí tu voz. ¿Eres tú quien habló?")
            BigButton("Sí, soy yo", onClick = controls.onConfirmOwner)
            BigSecondaryButton("No, ignorar", onClick = controls.onIgnoreUnknown)
        }

        if (controls.suggestVoiceRegistration) {
            InfoCard("Para obedecer solo a ti, necesito aprender cómo suena tu voz. Es un minuto: leerás 5 frases cortas.")
            BigSecondaryButton("Registrar mi voz", onClick = controls.onRegisterVoice)
        }

    }
}

/** El botón más importante de la app: grande, arriba y con texto claro. */
@Composable
private fun TalkButton(onTalk: () -> Unit) {
    Button(
        onClick = onTalk,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
    ) {
        Text(
            "Toca aquí y háblame",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatusCard(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}
