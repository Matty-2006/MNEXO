package ec.mnexo.understanding

import kotlin.math.max
import kotlin.math.min

/** Herramientas para comparar lo que dijo la persona con frases conocidas, tolerando errores. */
object TextMatching {

    private val accents = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )

    /**
     * Deja el texto en una forma comparable: minúsculas, sin tildes ni signos y con espacios simples.
     * "¿Qué HORA es?" → "que hora es"
     */
    fun normalize(text: String): String =
        text.lowercase()
            .map { accents[it] ?: it }
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Distancia de Levenshtein: cuántas letras hay que cambiar, agregar o quitar para pasar de [a] a [b].
     * Programación dinámica con dos filas: tiempo O(n·m) y memoria O(m).
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Parecido entre dos palabras: 1 = iguales, 0 = nada que ver. */
    fun similarity(a: String, b: String): Float {
        val longest = max(a.length, b.length)
        return if (longest == 0) 1f else 1f - levenshtein(a, b).toFloat() / longest
    }

    /**
     * ¿La palabra dicha corresponde a la palabra clave? Acepta errores del reconocimiento de voz
     * y variaciones ("grandes" ≈ "grande", "repiteme" ≈ "repite") en palabras de 5 o más letras.
     */
    fun wordMatches(spoken: String, keyword: String): Boolean {
        if (spoken == keyword) return true
        if (keyword.length < 4 || spoken.length < 4) return false
        if (keyword.length >= 5 && spoken.startsWith(keyword.take(5)) && keyword.startsWith(spoken.take(5))) return true
        return similarity(spoken, keyword) >= 0.8f
    }

    /**
     * Busca una expresión de una o varias palabras ("mas despacio") dentro de las palabras dichas,
     * en orden y seguidas. Devuelve la posición donde empieza, o -1.
     */
    fun indexOfPhrase(spoken: List<String>, phrase: List<String>): Int {
        if (phrase.isEmpty() || phrase.size > spoken.size) return -1
        for (start in 0..spoken.size - phrase.size) {
            if (phrase.indices.all { wordMatches(spoken[start + it], phrase[it]) }) return start
        }
        return -1
    }
}
