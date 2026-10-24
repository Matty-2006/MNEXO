package ec.mnexo.data.settings

import ec.mnexo.voice.SpeechRate

const val DEFAULT_ASSISTANT_NAME = "MNEXO"
const val MAX_NAME_LENGTH = 30

/** Tamaño de letra elegido por la persona. Se suma al tamaño que ya tenga configurado el sistema. */
enum class TextSize(val scale: Float, val label: String) {
    NORMAL(1.0f, "Normal"),
    GRANDE(1.25f, "Grande"),
    MUY_GRANDE(1.5f, "Muy grande"),
    ENORME(1.8f, "Enorme");

    companion object {
        /** Si el valor guardado no se reconoce (archivo antiguo o dañado), se usa GRANDE. */
        fun fromStored(value: String?): TextSize = entries.firstOrNull { it.name == value } ?: GRANDE
    }
}

data class UserSettings(
    val userName: String = "",
    val assistantName: String = DEFAULT_ASSISTANT_NAME,
    val textSize: TextSize = TextSize.GRANDE,
    val highContrast: Boolean = false,
    val speechRate: SpeechRate = SpeechRate.PAUSADA,
    /** Si el asistente saluda en voz alta al abrir la app. */
    val speakOnOpen: Boolean = true,
    val onboardingCompleted: Boolean = false,
)

/**
 * Limpia un nombre escrito o dictado: deja solo letras, espacios, apóstrofos y guiones,
 * quita espacios repetidos y limita la longitud.
 */
fun sanitizeName(raw: String): String =
    raw.filter { it.isLetter() || it == ' ' || it == '\'' || it == '-' }
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .take(MAX_NAME_LENGTH)
        .trim()

/** El asistente siempre necesita un nombre: si queda vacío, vuelve a llamarse MNEXO. */
fun sanitizeAssistantName(raw: String): String = sanitizeName(raw).ifEmpty { DEFAULT_ASSISTANT_NAME }
