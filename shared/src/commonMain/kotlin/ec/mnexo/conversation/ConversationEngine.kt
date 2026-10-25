package ec.mnexo.conversation

import ec.mnexo.data.settings.TextSize
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.understanding.IntentType
import ec.mnexo.understanding.Understander
import ec.mnexo.understanding.Understanding

/** Acciones que la app ejecuta tras entender a la persona. Todas son de riesgo bajo: no requieren confirmación. */
sealed interface AppAction {
    data object None : AppAction
    data object StopSpeaking : AppAction
    data object RepeatLast : AppAction

    /** Baja un nivel la velocidad, la guarda y repite la última frase. */
    data object RepeatSlower : AppAction
    data object OpenSettings : AppAction

    /** Guardar un cambio de preferencias (letra, contraste, velocidad). */
    data class UpdateSettings(val settings: UserSettings) : AppAction
}

/**
 * Respuesta de la conversación.
 * @param reply lo que el asistente dice (null = no hablar, por ejemplo tras "cállate").
 */
data class Outcome(val understanding: Understanding, val reply: String?, val action: AppAction)

/** Lo que el asistente recuerda de la conversación para entender frases como "un poco más". */
data class ConversationContext(
    /** Último ajuste hecho (letra más grande, más despacio...), para repetirlo con "más". */
    val lastAdjustment: IntentType? = null,
)

/**
 * Decide qué hacer con cada frase: entiende la intención, prepara la acción y la respuesta.
 * No ejecuta nada por sí mismo (la app lo hace), así se puede probar sin pantalla ni voz.
 */
class ConversationEngine(
    private val understander: Understander,
    private val time: TimeProvider = SystemTime,
) {
    fun respond(text: String, settings: UserSettings, context: ConversationContext): Pair<Outcome, ConversationContext> {
        val understanding = understander.understand(text)
        // "Un poco más" repite el último ajuste (por ejemplo, agrandar otra vez la letra).
        val intent = if (understanding.intent == IntentType.AGAIN) context.lastAdjustment ?: IntentType.AGAIN else understanding.intent
        val outcome = outcomeFor(intent, understanding, settings)
        val newContext = if (intent in ADJUSTMENTS) context.copy(lastAdjustment = intent) else context
        return outcome to newContext
    }

    private fun outcomeFor(intent: IntentType, understanding: Understanding, settings: UserSettings): Outcome {
        fun say(reply: String?, action: AppAction = AppAction.None) = Outcome(understanding, reply, action)
        fun save(changed: UserSettings, reply: String) = say(reply, AppAction.UpdateSettings(changed))

        /** Respuesta honesta para lo que todavía no sabe hacer: nunca finge haberlo hecho. */
        fun notYet(what: String): Outcome {
            val forWhom = understanding.slots["contacto"]?.let { " (entendí que es para $it)" }.orEmpty()
            return say(
                "Todavía no sé $what$forWhom, pero lo estoy aprendiendo. " +
                    "Por ahora puedo repetir, hablar más despacio o cambiar el tamaño de la letra.",
            )
        }

        return when (intent) {
            IntentType.STOP -> say(null, AppAction.StopSpeaking)
            IntentType.REPEAT -> say(null, AppAction.RepeatLast)

            // "No entendí" / "más despacio": repetir lo último, más lento (y dejarlo así).
            IntentType.REPEAT_SLOWER -> say(null, AppAction.RepeatSlower)

            IntentType.SPEAK_FASTER -> {
                val faster = settings.speechRate.faster()
                if (faster == settings.speechRate) {
                    say("Ya hablo lo más rápido que puedo.")
                } else {
                    save(settings.copy(speechRate = faster), "De acuerdo, hablaré un poco más rápido.")
                }
            }

            IntentType.TEXT_BIGGER -> {
                val bigger = TextSize.entries.getOrNull(settings.textSize.ordinal + 1)
                if (bigger == null) {
                    say("Las letras ya están en el tamaño más grande. También puedo activar el alto contraste.")
                } else {
                    save(settings.copy(textSize = bigger), "Listo, puse las letras más grandes.")
                }
            }

            IntentType.TEXT_SMALLER -> {
                val smaller = TextSize.entries.getOrNull(settings.textSize.ordinal - 1)
                if (smaller == null) {
                    say("Las letras ya están en el tamaño más pequeño.")
                } else {
                    save(settings.copy(textSize = smaller), "Listo, puse las letras más pequeñas.")
                }
            }

            IntentType.CONTRAST_ON ->
                if (settings.highContrast) {
                    say("El alto contraste ya está activado.")
                } else {
                    save(settings.copy(highContrast = true), "Listo, activé el alto contraste.")
                }

            IntentType.CONTRAST_OFF ->
                if (!settings.highContrast) {
                    say("El alto contraste ya está desactivado.")
                } else {
                    save(settings.copy(highContrast = false), "Listo, volví a los colores normales.")
                }

            IntentType.OPEN_SETTINGS -> say("Abro los ajustes.", AppAction.OpenSettings)
            IntentType.WHAT_TIME -> say(SpanishClock.time(time.now()))
            IntentType.WHAT_DATE -> say(SpanishClock.date(time.now()))
            IntentType.WHO_ARE_YOU -> say("Me llamo ${settings.assistantName}. Soy tu asistente y estoy aquí para ayudarte.")
            IntentType.HELP -> say(HELP_TEXT)

            IntentType.GREETING -> {
                val name = settings.userName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
                say("¡Hola$name! ¿En qué te ayudo?")
            }

            IntentType.THANKS -> say("Con mucho gusto. ¿Necesitas algo más?")
            IntentType.YES -> say("De acuerdo. ¿Qué necesitas?")
            IntentType.NO -> say("Está bien. Aquí estaré cuando me necesites.")
            IntentType.AGAIN -> say("¿Qué quieres que haga otra vez? Por ejemplo: letras más grandes o más despacio.")

            IntentType.CREATE_DOCUMENT -> notYet("crear cartas y documentos")
            IntentType.CREATE_SURVEY -> notYet("crear encuestas")
            IntentType.CALL -> notYet("hacer llamadas")
            IntentType.SEND_MESSAGE -> notYet("enviar mensajes")
            IntentType.ORDER_FOOD -> notYet("ayudarte a pedir comida")
            IntentType.OPEN_APP -> notYet("abrir otras aplicaciones")

            IntentType.UNKNOWN -> say(
                "No entendí «${understanding.text}». Puedes decirme, por ejemplo: «repite», «más despacio», " +
                    "«letras más grandes» o «¿qué puedes hacer?».",
            )
        }
    }

    private companion object {
        val ADJUSTMENTS = setOf(
            IntentType.TEXT_BIGGER, IntentType.TEXT_SMALLER, IntentType.REPEAT_SLOWER, IntentType.SPEAK_FASTER,
        )

        const val HELP_TEXT = "Por ahora puedo: repetir lo que dije, hablar más despacio o más rápido, " +
            "poner las letras más grandes o más pequeñas, activar el alto contraste, y decirte la hora y la fecha. " +
            "Pronto podré crear cartas, hacer llamadas y más."
    }
}
