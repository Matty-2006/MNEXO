package ec.mnexo.understanding

import ec.mnexo.understanding.IntentType.AGAIN
import ec.mnexo.understanding.IntentType.CALL
import ec.mnexo.understanding.IntentType.CONTRAST_OFF
import ec.mnexo.understanding.IntentType.CONTRAST_ON
import ec.mnexo.understanding.IntentType.CREATE_DOCUMENT
import ec.mnexo.understanding.IntentType.CREATE_SURVEY
import ec.mnexo.understanding.IntentType.GREETING
import ec.mnexo.understanding.IntentType.HELP
import ec.mnexo.understanding.IntentType.NO
import ec.mnexo.understanding.IntentType.OPEN_APP
import ec.mnexo.understanding.IntentType.OPEN_SETTINGS
import ec.mnexo.understanding.IntentType.ORDER_FOOD
import ec.mnexo.understanding.IntentType.REPEAT
import ec.mnexo.understanding.IntentType.REPEAT_SLOWER
import ec.mnexo.understanding.IntentType.SEND_MESSAGE
import ec.mnexo.understanding.IntentType.SPEAK_FASTER
import ec.mnexo.understanding.IntentType.STOP
import ec.mnexo.understanding.IntentType.TEXT_BIGGER
import ec.mnexo.understanding.IntentType.TEXT_SMALLER
import ec.mnexo.understanding.IntentType.THANKS
import ec.mnexo.understanding.IntentType.WHAT_DATE
import ec.mnexo.understanding.IntentType.WHAT_TIME
import ec.mnexo.understanding.IntentType.WHO_ARE_YOU
import ec.mnexo.understanding.IntentType.YES

/**
 * Una regla: todas las [groups] deben aparecer en la frase (en cada grupo basta una de sus expresiones).
 * @param maxWords la regla solo aplica a frases cortas (para palabras ambiguas como "para" o "como").
 * @param negatable si la frase dice "no" justo antes, la regla no aplica ("no repitas").
 */
private class Rule(
    val intent: IntentType,
    val groups: List<List<String>>,
    val maxWords: Int = Int.MAX_VALUE,
    val negatable: Boolean = true,
)

private fun rule(intent: IntentType, vararg alternatives: String, maxWords: Int = Int.MAX_VALUE, negatable: Boolean = true) =
    Rule(intent, listOf(alternatives.toList()), maxWords, negatable)

private fun rule(intent: IntentType, first: List<String>, second: List<String>) = Rule(intent, listOf(first, second))

private val TEXT_WORDS = listOf("letra", "letras", "letritas", "texto", "palabras", "pantalla")

/**
 * Catálogo de frases en español (sin tildes, como quedan tras normalizar). El orden importa en empates:
 * van primero las reglas más específicas.
 */
private val CATALOG: List<Rule> = listOf(
    // --- Detener ---
    rule(STOP, "deja de hablar", "ya no hables", "callate", "no hables", "silencio por favor", negatable = false),
    // Solo frases de 1-2 palabras: "para" también aparece en "una carta para mi hija".
    rule(STOP, "para", "detente", "calla", "silencio", "basta", "alto", "cancela", "ya", "suficiente", maxWords = 2),

    // --- No entendí → más despacio (frase muy común en personas mayores) ---
    rule(
        REPEAT_SLOWER, "no entendi", "no te entendi", "no le entendi", "no entiendo", "no comprendi",
        "mas despacio", "mas despacito", "mas lento", "despacio", "despacito", "mas pausado", "habla lento",
        negatable = false,
    ),
    rule(SPEAK_FASTER, "mas rapido", "mas ligero", "apurate", "habla rapido", "rapido", "acelera"),

    // --- Repetir ---
    rule(
        REPEAT, "repite", "repitelo", "repiteme", "repetir", "repetirlo", "vuelve a decir", "vuelvelo a decir",
        "dilo otra vez", "dilo de nuevo", "que dijiste", "como dijiste", "no te escuche", "no escuche",
        "otra vez", "de nuevo",
    ),
    rule(REPEAT, "mande", "que", "como", "perdon", "disculpa", "eh", maxWords = 2),
    rule(AGAIN, "mas", "un poco mas", "todavia mas", "aun mas", "mas todavia", maxWords = 3),

    // --- Accesibilidad ---
    rule(
        TEXT_BIGGER, TEXT_WORDS,
        listOf("grande", "grandes", "mas grande", "agranda", "agrandar", "aumenta", "aumentar", "mas grandes", "sube"),
    ),
    rule(
        TEXT_BIGGER, "no veo", "no alcanzo a leer", "no puedo leer", "no leo bien", "veo chiquito",
        "muy pequeno", "muy chiquito", "no se ve", negatable = false,
    ),
    rule(
        TEXT_SMALLER, TEXT_WORDS,
        listOf("pequena", "pequenas", "chica", "chicas", "chiquita", "chiquitas", "mas pequena", "reduce", "achica", "disminuye", "baja"),
    ),
    rule(CONTRAST_OFF, listOf("contraste"), listOf("quita", "quitale", "desactiva", "apaga", "normal", "sin", "saca")),
    rule(CONTRAST_OFF, "fondo blanco", "fondo claro", "modo claro", "colores normales"),
    rule(CONTRAST_ON, listOf("contraste"), listOf("activa", "pon", "ponle", "alto", "sube", "mas", "prende", "enciende")),
    rule(CONTRAST_ON, "fondo negro", "fondo oscuro", "modo oscuro"),
    rule(OPEN_SETTINGS, "ajustes", "configuracion", "configurar", "opciones", "preferencias"),

    // --- Información ---
    rule(WHAT_TIME, "que hora", "la hora", "hora es", "horas son"),
    rule(WHAT_DATE, "que dia", "que fecha", "la fecha", "dia es hoy", "fecha de hoy", "en que mes", "que ano estamos"),
    rule(WHO_ARE_YOU, "quien eres", "como te llamas", "tu nombre", "que eres"),
    rule(
        HELP, "que puedes hacer", "que sabes hacer", "en que me ayudas", "como funcionas", "como te uso",
        "que puedo pedirte", "que puedo decirte", "no se que hacer", "no se que decir",
    ),
    rule(HELP, "ayuda", "ayudame", "auxilio", maxWords = 2),

    // --- Tareas de próximos pasos (se reconocen para responder con honestidad) ---
    rule(CREATE_SURVEY, "encuesta", "cuestionario"),
    rule(CREATE_DOCUMENT, "carta", "documento", "oficio", "solicitud", "escrito", "constancia", "word"),
    rule(ORDER_FOOD, "pedir comida", "comida", "combo", "kfc", "uber eats", "pedido", "almuerzo", "pizza", "pollo"),
    rule(SEND_MESSAGE, "mensaje", "whatsapp", "escribele", "mandale", "enviale", "manda un", "envia un"),
    rule(CALL, "llama", "llamar", "llamale", "llamada", "marca", "marcale", "marcar", "telefonea", "comunicame"),
    rule(OPEN_APP, "abre", "abrir", "abreme", "entra a", "entrar a", "aplicacion"),

    // --- Cortesía ---
    rule(THANKS, "gracias", "te agradezco", "muy amable", "dios te pague"),
    rule(GREETING, "hola", "buenos dias", "buenas tardes", "buenas noches", "buen dia", "como estas", "que tal", maxWords = 6),
    rule(YES, "si", "claro", "dale", "de acuerdo", "ok", "okey", "bueno", "correcto", "hazlo", "esta bien", "por supuesto", "listo", maxWords = 3),
    rule(NO, "no", "nada", "no gracias", "ninguno", "no quiero", "mejor no", maxWords = 3, negatable = false),
)

/** Palabras de relleno que no cambian el sentido. */
private val FILLERS = listOf("por favor", "porfa", "porfavor", "oye", "hey", "disculpa que te moleste")

/**
 * Nivel 1 de comprensión: catálogo de reglas + similitud de palabras (Levenshtein).
 * Instantáneo, sin internet y funciona en cualquier equipo, incluso con 2 GB de RAM.
 */
class RuleBasedUnderstander(private val assistantName: () -> String = { "MNEXO" }) : Understander {

    override fun understand(text: String): Understanding {
        val words = cleanWords(text)
        if (words.isEmpty()) return Understanding(IntentType.UNKNOWN, 0f, text)

        var best: Pair<Rule, Int>? = null // regla y cantidad de palabras que explica
        for (rule in CATALOG) {
            if (words.size > rule.maxWords) continue
            val covered = matchRule(rule, words) ?: continue
            val currentBest = best
            // Gana la regla que explica más palabras; en empate, la primera del catálogo.
            if (currentBest == null || covered > currentBest.second) best = rule to covered
        }

        val (rule, covered) = best ?: return Understanding(IntentType.UNKNOWN, 0f, text)
        val confidence = (covered.toFloat() / words.size).coerceIn(0f, 1f)
        return Understanding(rule.intent, confidence, text, extractSlots(rule.intent, words))
    }

    /** Palabras de la frase sin relleno ni el nombre del asistente ("Oye MNEXO, repite" → "repite"). */
    private fun cleanWords(text: String): List<String> {
        var normalized = " " + TextMatching.normalize(text) + " "
        val name = TextMatching.normalize(assistantName())
        if (name.isNotEmpty()) normalized = normalized.replace(" $name ", " ")
        for (filler in FILLERS) normalized = normalized.replace(" $filler ", " ")
        return normalized.split(' ').filter { it.isNotEmpty() }
    }

    /** Si la regla coincide, devuelve cuántas palabras de la frase explica; si no, null. */
    private fun matchRule(rule: Rule, words: List<String>): Int? {
        var covered = 0
        var firstIndex = Int.MAX_VALUE
        for (group in rule.groups) {
            var bestInGroup: Pair<Int, Int>? = null // posición y largo de la expresión encontrada
            for (alternative in group) {
                val phrase = alternative.split(' ')
                val index = TextMatching.indexOfPhrase(words, phrase)
                if (index >= 0 && (bestInGroup == null || phrase.size > bestInGroup.second)) {
                    bestInGroup = index to phrase.size
                }
            }
            val (index, length) = bestInGroup ?: return null
            firstIndex = minOf(firstIndex, index)
            covered += length
        }
        // "No repitas", "no pongas las letras más grandes": un "no" en las 3 palabras previas anula la orden.
        // Ante la duda es más seguro no actuar y volver a preguntar.
        if (rule.negatable && words.subList(maxOf(0, firstIndex - 3), firstIndex).contains("no")) return null
        return covered
    }

    /** Extrae datos útiles: a quién llamar o escribir ("llama a mi hija maria" → "mi hija maria"). */
    private fun extractSlots(intent: IntentType, words: List<String>): Map<String, String> {
        if (intent != CALL && intent != SEND_MESSAGE) return emptyMap()
        val afterA = words.indexOf("a").takeIf { it >= 0 && it < words.lastIndex } ?: return emptyMap()
        return mapOf("contacto" to words.drop(afterA + 1).joinToString(" "))
    }
}
