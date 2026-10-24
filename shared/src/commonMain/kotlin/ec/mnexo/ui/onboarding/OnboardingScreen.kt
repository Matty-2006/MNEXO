package ec.mnexo.ui.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ec.mnexo.data.settings.TextSize
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.data.settings.sanitizeAssistantName
import ec.mnexo.data.settings.sanitizeName
import ec.mnexo.platform.SystemBackHandler
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.BigRadioOption
import ec.mnexo.ui.components.BigSecondaryButton
import ec.mnexo.ui.components.BigSwitchRow
import ec.mnexo.ui.components.BodyText
import ec.mnexo.ui.components.NameField
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle
import ec.mnexo.ui.theme.MnexoTheme

private const val STEP_WELCOME = 0
private const val STEP_USER_NAME = 1
private const val STEP_ASSISTANT_NAME = 2
private const val STEP_APPEARANCE = 3
private const val STEP_SUMMARY = 4

/**
 * Configuración inicial: una sola decisión por pantalla.
 * Los cambios de letra y contraste se ven al instante, antes de guardarlos.
 */
@Composable
fun OnboardingScreen(initial: UserSettings, onFinish: (UserSettings) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var userName by rememberSaveable { mutableStateOf(initial.userName) }
    var assistantName by rememberSaveable { mutableStateOf(initial.assistantName) }
    var textSizeName by rememberSaveable { mutableStateOf(initial.textSize.name) }
    var highContrast by rememberSaveable { mutableStateOf(initial.highContrast) }
    val textSize = TextSize.fromStored(textSizeName)

    SystemBackHandler(enabled = step > STEP_WELCOME) { step-- }

    MnexoTheme(textSize = textSize, highContrast = highContrast) {
        ScreenColumn {
            if (step > STEP_WELCOME) {
                Text(
                    text = "Paso $step de $STEP_SUMMARY",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (step) {
                STEP_WELCOME -> {
                    ScreenTitle("Hola. Soy tu asistente.")
                    BodyText("Antes de empezar, voy a hacerte unas preguntas cortas para adaptarme a ti.")
                    BodyText("Puedes cambiar todo después en Ajustes.")
                    BigButton("Empezar", onClick = { step = STEP_USER_NAME })
                }

                STEP_USER_NAME -> {
                    ScreenTitle("¿Cómo te llamas?")
                    BodyText("Lo usaré para saludarte. Se guarda solo en este equipo.")
                    NameField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = "Tu nombre",
                        onDone = { step = STEP_ASSISTANT_NAME },
                    )
                    BigButton("Continuar", onClick = { step = STEP_ASSISTANT_NAME })
                    BigSecondaryButton("Prefiero no decirlo", onClick = {
                        userName = ""
                        step = STEP_ASSISTANT_NAME
                    })
                    BigSecondaryButton("Atrás", onClick = { step = STEP_WELCOME })
                }

                STEP_ASSISTANT_NAME -> {
                    ScreenTitle("¿Cómo quieres llamarme?")
                    BodyText("Puedes dejar MNEXO o elegir otro nombre que te resulte fácil.")
                    NameField(
                        value = assistantName,
                        onValueChange = { assistantName = it },
                        label = "Nombre del asistente",
                        onDone = { step = STEP_APPEARANCE },
                    )
                    BigButton("Continuar", onClick = { step = STEP_APPEARANCE })
                    BigSecondaryButton("Atrás", onClick = { step = STEP_USER_NAME })
                }

                STEP_APPEARANCE -> {
                    ScreenTitle("¿Qué tamaño de letra prefieres?")
                    BodyText("Toca una opción y mira cómo cambia esta pantalla.")
                    TextSize.entries.forEach { size ->
                        BigRadioOption(
                            text = size.label,
                            selected = size == textSize,
                            onSelect = { textSizeName = size.name },
                        )
                    }
                    BigSwitchRow(
                        text = "Alto contraste (fondo negro, letras claras)",
                        checked = highContrast,
                        onCheckedChange = { highContrast = it },
                    )
                    BigButton("Continuar", onClick = { step = STEP_SUMMARY })
                    BigSecondaryButton("Atrás", onClick = { step = STEP_ASSISTANT_NAME })
                }

                else -> {
                    val cleanUserName = sanitizeName(userName)
                    val cleanAssistantName = sanitizeAssistantName(assistantName)
                    ScreenTitle(if (cleanUserName.isEmpty()) "Todo listo." else "Todo listo, $cleanUserName.")
                    BodyText("Me llamo $cleanAssistantName.")
                    if (cleanUserName.isEmpty()) {
                        BodyText("No me dijiste tu nombre. Puedes agregarlo cuando quieras en Ajustes.")
                    }
                    BodyText("Letra: ${textSize.label}. Alto contraste: ${if (highContrast) "sí" else "no"}.")
                    BigButton("Empezar a usar $cleanAssistantName", onClick = {
                        onFinish(
                            UserSettings(
                                userName = cleanUserName,
                                assistantName = cleanAssistantName,
                                textSize = textSize,
                                highContrast = highContrast,
                            ),
                        )
                    })
                    BigSecondaryButton("Cambiar algo", onClick = { step = STEP_USER_NAME })
                }
            }
        }
    }
}
