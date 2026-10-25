package ec.mnexo.ui.voiceprint

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ec.mnexo.listening.ListenerState
import ec.mnexo.platform.SystemBackHandler
import ec.mnexo.presentation.EnrollmentUi
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.BigSecondaryButton
import ec.mnexo.ui.components.BodyText
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle
import ec.mnexo.voiceprint.VoicePrints

/** Registro de la voz del dueño: 5 frases cortas, una por pantalla. */
@Composable
fun EnrollmentScreen(
    state: EnrollmentUi,
    listenerState: ListenerState,
    onRecord: () -> Unit,
    onHearPhrase: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modelSizeMb: Long,
    onDownload: () -> Unit,
) {
    val total = VoicePrints.ENROLLMENT_PHRASES.size
    SystemBackHandler(onBack = onClose)

    ScreenColumn {
        // Para reconocer la voz hace falta el oído (incluye el modelo de quién habla).
        val needsDownload = listenerState == ListenerState.NeedsModel ||
            listenerState is ListenerState.Downloading ||
            listenerState == ListenerState.Unavailable(ec.mnexo.listening.ListenProblem.DOWNLOAD_FAILED)
        if (needsDownload) {
            ScreenTitle("Primero necesito mi oído")
            if (listenerState is ListenerState.Downloading) {
                Message("Descargando… ${(listenerState.progress * 100).toInt()} %")
                LinearProgressIndicator(progress = { listenerState.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                BodyText("Para aprender tu voz necesito descargar mi oído ($modelSizeMb MB), una sola vez. Es mejor con Wi-Fi.")
                BigButton("Descargar ahora", onClick = onDownload)
            }
            BigSecondaryButton("Volver", onClick = onClose)
            return@ScreenColumn
        }

        if (state.finished) {
            ScreenTitle("Listo, ya reconozco tu voz")
            BodyText("Desde ahora solo obedeceré a tu voz. Si alguna vez no te reconozco, podrás tocar «Sí, soy yo».")
            val consistency = state.consistency ?: 1f
            if (consistency < VoicePrints.GOOD_CONSISTENCY) {
                Message(
                    "Tus frases sonaron algo distintas entre sí (quizá había ruido). " +
                        "Si luego no te reconozco bien, vuelve a registrar tu voz en un lugar más tranquilo.",
                )
                BigSecondaryButton("Registrar otra vez ahora", onClick = onRestart)
            }
            BigButton("Terminar", onClick = onClose)
            return@ScreenColumn
        }

        Text(
            text = "Frase ${state.phraseIndex + 1} de $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { state.phraseIndex.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        ScreenTitle("Lee esta frase en voz alta")
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "«${VoicePrints.ENROLLMENT_PHRASES[state.phraseIndex]}»",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(24.dp),
            )
        }

        when {
            state.recording && listenerState == ListenerState.Listening -> Message("Te escucho… lee la frase ahora.")
            state.recording -> Message("Un momento…")
            else -> {
                state.message?.let { Message(it) }
                BigButton("Grabar esta frase", onClick = onRecord)
                BigSecondaryButton("Escuchar la frase primero", onClick = onHearPhrase)
            }
        }
        BodyText("Tu voz se guarda cifrada solo en este equipo. Puedes borrarla cuando quieras en Ajustes.")
        BigSecondaryButton("Cancelar", onClick = onClose)
    }
}

@Composable
private fun Message(text: String) {
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
