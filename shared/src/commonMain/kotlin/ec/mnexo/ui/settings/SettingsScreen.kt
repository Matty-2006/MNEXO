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
import ec.mnexo.voice.VoiceSource
import ec.mnexo.voiceprint.VoiceCheckLevel

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
    hasVoiceProfile: Boolean,
    onRegisterVoice: () -> Unit,
    onDeleteVoiceProfile: () -> Unit,
) {
    var userName by rememberSaveable { mutableStateOf(settings.userName) }
    var assistantName by rememberSaveable { mutableStateOf(settings.assistantName) }
    var textSizeName by rememberSaveable { mutableStateOf(settings.textSize.name) }
    var highContrast by rememberSaveable { mutableStateOf(settings.highContrast) }
    var speechRateName by rememberSaveable { mutableStateOf(settings.speechRate.name) }
    var speakOnOpen by rememberSaveable { mutableStateOf(settings.speakOnOpen) }
    var voiceSourceName by rememberSaveable { mutableStateOf(settings.voiceSource.name) }
    var ownerVoiceOnly by rememberSaveable { mutableStateOf(settings.ownerVoiceOnly) }
    var voiceCheckLevelName by rememberSaveable { mutableStateOf(settings.voiceCheckLevel.name) }
    var confirmDeleteVoice by rememberSaveable { mutableStateOf(false) }
    val voiceCheckLevel = VoiceCheckLevel.fromStored(voiceCheckLevelName)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val textSize = TextSize.fromStored(textSizeName)
    val speechRate = SpeechRate.fromStored(speechRateName)
    val voiceSource = VoiceSource.fromStored(voiceSourceName)

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
            BodyText("¿Qué voz uso? Toca una opción para escucharla.")
            VoiceSource.entries.forEach { option ->
                BigRadioOption(
                    text = option.label,
                    selected = option == voiceSource,
                    onSelect = {
                        voiceSourceName = option.name
                        voice.onPreviewSource(option)
                    },
                )
            }
            BodyText("¿A qué velocidad hablo? Toca una para escucharla.")
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

            SectionTitle("Mi voz")
            BodyText(
                if (hasVoiceProfile) {
                    "Ya registraste tu voz. Se guarda cifrada solo en este equipo."
                } else {
                    "Aún no registraste tu voz: por ahora obedezco a cualquier persona."
                },
            )
            BigSecondaryButton(
                text = if (hasVoiceProfile) "Volver a registrar mi voz" else "Registrar mi voz",
                onClick = onRegisterVoice,
            )
            BigSwitchRow(
                text = "Obedecer solo a mi voz",
                checked = ownerVoiceOnly,
                onCheckedChange = { ownerVoiceOnly = it },
            )
            if (ownerVoiceOnly) {
                BodyText("¿Qué tan exigente soy al reconocerte?")
                VoiceCheckLevel.entries.forEach { level ->
                    BigRadioOption(
                        text = level.label,
                        selected = level == voiceCheckLevel,
                        onSelect = { voiceCheckLevelName = level.name },
                    )
                }
                BodyText(
                    "Importante: reconocer la voz evita que obedezca a la tele o a visitas, pero no es una " +
                        "cerradura: una grabación de tu voz podría engañarme. Para lo delicado siempre pediré confirmación.",
                )
            }
            if (hasVoiceProfile) {
                BigSecondaryButton("Borrar mi huella de voz", onClick = { confirmDeleteVoice = true })
            }

            BigButton("Guardar cambios", onClick = {
                onSave(
                    settings.copy(
                        userName = userName,
                        assistantName = assistantName,
                        textSize = textSize,
                        highContrast = highContrast,
                        speechRate = speechRate,
                        voiceSource = voiceSource,
                        speakOnOpen = speakOnOpen,
                        ownerVoiceOnly = ownerVoiceOnly,
                        voiceCheckLevel = voiceCheckLevel,
                    ),
                )
            })
            BigSecondaryButton("Volver sin guardar", onClick = onBack)

            Spacer(Modifier.height(24.dp))
            SectionTitle("Privacidad")
            BodyText("Tus datos (incluida tu huella de voz) se guardan solo en este equipo. Puedes borrarlos cuando quieras.")
            BigSecondaryButton("Borrar mis datos", onClick = { confirmDelete = true })
        }

        if (confirmDeleteVoice) {
            AlertDialog(
                onDismissRequest = { confirmDeleteVoice = false },
                title = { Text("¿Borrar tu huella de voz?", style = MaterialTheme.typography.headlineMedium) },
                text = {
                    Text(
                        "Dejaré de reconocer tu voz hasta que la registres otra vez.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("Sí, borrar", onClick = {
                            confirmDeleteVoice = false
                            onDeleteVoiceProfile()
                        })
                        BigSecondaryButton("No, cancelar", onClick = { confirmDeleteVoice = false })
                    }
                },
            )
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
