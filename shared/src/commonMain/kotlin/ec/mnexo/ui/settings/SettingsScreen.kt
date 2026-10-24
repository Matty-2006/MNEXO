package ec.mnexo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ec.mnexo.data.settings.TextSize
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.BigRadioOption
import ec.mnexo.ui.components.BigSecondaryButton
import ec.mnexo.ui.components.BigSwitchRow
import ec.mnexo.ui.components.BodyText
import ec.mnexo.ui.components.NameField
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle
import ec.mnexo.ui.components.VoiceControls
import ec.mnexo.ui.components.VoiceStatus
import ec.mnexo.ui.theme.MnexoTheme
import ec.mnexo.voice.SpeechRate

/**
 * Ajustes: los cambios se previsualizan al instante (y la velocidad se escucha), pero solo
 * se guardan con "Guardar cambios". "Volver sin guardar" descarta lo que no se guardó.
 */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    voice: VoiceControls,
    onSave: (UserSettings) -> Unit,
    onBack: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    var userName by rememberSaveable { mutableStateOf(settings.userName) }
    var assistantName by rememberSaveable { mutableStateOf(settings.assistantName) }
    var textSizeName by rememberSaveable { mutableStateOf(settings.textSize.name) }
    var highContrast by rememberSaveable { mutableStateOf(settings.highContrast) }
    var speechRateName by rememberSaveable { mutableStateOf(settings.speechRate.name) }
    var speakOnOpen by rememberSaveable { mutableStateOf(settings.speakOnOpen) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val textSize = TextSize.fromStored(textSizeName)
    val speechRate = SpeechRate.fromStored(speechRateName)

    MnexoTheme(textSize = textSize, highContrast = highContrast) {
        ScreenColumn {
            ScreenTitle("Ajustes")
            NameField(value = userName, onValueChange = { userName = it }, label = "Tu nombre", onDone = {})
            NameField(
                value = assistantName,
                onValueChange = { assistantName = it },
                label = "Nombre del asistente",
                onDone = {},
            )

            SectionTitle("Tamaño de letra")
            TextSize.entries.forEach { size ->
                BigRadioOption(text = size.label, selected = size == textSize, onSelect = { textSizeName = size.name })
            }
            BigSwitchRow(text = "Alto contraste", checked = highContrast, onCheckedChange = { highContrast = it })

            SectionTitle("Voz")
            BodyText("Toca una velocidad para escucharla.")
            SpeechRate.entries.forEach { rate ->
                BigRadioOption(
                    text = rate.label,
                    selected = rate == speechRate,
                    onSelect = {
                        speechRateName = rate.name
                        voice.onPreviewRate(rate)
                    },
                )
            }
            BigSwitchRow(
                text = "Saludarme en voz alta al abrir",
                checked = speakOnOpen,
                onCheckedChange = { speakOnOpen = it },
            )
            VoiceStatus(state = voice.state, onOpenVoiceSetup = voice.onOpenVoiceSetup)

            BigButton("Guardar cambios", onClick = {
                onSave(
                    settings.copy(
                        userName = userName,
                        assistantName = assistantName,
                        textSize = textSize,
                        highContrast = highContrast,
                        speechRate = speechRate,
                        speakOnOpen = speakOnOpen,
                    ),
                )
            })
            BigSecondaryButton("Volver sin guardar", onClick = onBack)

            Spacer(Modifier.height(24.dp))
            SectionTitle("Privacidad")
            BodyText("Tus datos se guardan solo en este equipo. Puedes borrarlos cuando quieras.")
            BigSecondaryButton("Borrar mis datos", onClick = { confirmDelete = true })
        }

        if (confirmDelete) {
            // Acción destructiva: siempre se pide confirmación explícita.
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("¿Borrar tus datos?", style = MaterialTheme.typography.headlineMedium) },
                text = {
                    Text(
                        "Se borrarán tu nombre y tus preferencias de este equipo. " +
                            "La app volverá a la configuración inicial. Esto no se puede deshacer.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("Sí, borrar", onClick = {
                            confirmDelete = false
                            onDeleteAllData()
                        })
                        BigSecondaryButton("No, cancelar", onClick = { confirmDelete = false })
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
