package ec.mnexo.understanding

/** Lo que la persona quiere. [available] = MNEXO ya sabe hacerlo; si no, lo dice con honestidad. */
enum class IntentType(val available: Boolean = true) {
    // Control de la conversación
    STOP,
    REPEAT,
    REPEAT_SLOWER,
    SPEAK_FASTER,
    AGAIN,

    // Accesibilidad
    TEXT_BIGGER,
    TEXT_SMALLER,
    CONTRAST_ON,
    CONTRAST_OFF,
    OPEN_SETTINGS,

    // Información
    WHAT_TIME,
    WHAT_DATE,
    WHO_ARE_YOU,
    HELP,

    // Cortesía y respuestas
    GREETING,
    THANKS,
    YES,
    NO,

    // Tareas que llegarán en próximos pasos
    CREATE_DOCUMENT(available = false),
    CREATE_SURVEY(available = false),
    CALL(available = false),
    SEND_MESSAGE(available = false),
    ORDER_FOOD(available = false),
    OPEN_APP(available = false),

    UNKNOWN,
}

/**
 * Resultado de entender una frase.
 * @param confidence 0 a 1: qué parte de la frase explicó la regla que coincidió.
 * @param slots datos extraídos, por ejemplo "contacto" → "mi hija maria".
 */
data class Understanding(
    val intent: IntentType,
    val confidence: Float,
    val text: String,
    val slots: Map<String, String> = emptyMap(),
)

/** Convierte una frase en una intención. El nivel 1 usa reglas locales; el nivel 2 (paso 1.6) usará IA local. */
fun interface Understander {
    fun understand(text: String): Understanding
}
