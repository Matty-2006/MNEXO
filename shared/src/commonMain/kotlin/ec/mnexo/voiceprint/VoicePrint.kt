package ec.mnexo.voiceprint

import kotlin.math.sqrt

/** Convierte un tramo de voz en una "huella numérica" (vector) que describe cómo suena esa persona. */
fun interface SpeakerEmbedder {
    fun embed(samples: FloatArray, sampleRate: Int): FloatArray
}

/**
 * Guarda la huella de voz del dueño, CIFRADA y solo en este equipo.
 * Es un dato biométrico (sensible según la LOPDP de Ecuador): nunca sale del equipo y se puede borrar.
 */
interface VoiceProfileStore {
    suspend fun load(): FloatArray?
    suspend fun save(profile: FloatArray)
    suspend fun delete()
}

/** Qué tan exigente es la comprobación de voz. */
enum class VoiceCheckLevel(val threshold: Float, val label: String) {
    FLEXIBLE(0.45f, "Flexible (me rechaza menos)"),
    NORMAL(0.55f, "Normal (recomendada)"),
    ESTRICTA(0.65f, "Estricta (más segura)");

    companion object {
        fun fromStored(value: String?): VoiceCheckLevel = entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

/** Huella del dueño y umbral: si el parecido es menor, la voz no se reconoce como suya. */
class VoiceCheck(val profile: FloatArray, val threshold: Float)

object VoicePrints {
    /** Frases para registrar la voz: variadas para captar distintos sonidos del español. */
    val ENROLLMENT_PHRASES = listOf(
        "Hola, soy yo y quiero usar mi asistente.",
        "Hoy es un buen día para aprender algo nuevo.",
        "Por favor, recuerda bien cómo suena mi voz.",
        "Mi familia es muy importante para mí.",
        "Uno, dos, tres, cuatro, cinco, seis y siete.",
    )

    /** Una frase de registro debe durar al menos esto para dar una huella fiable. */
    const val MIN_SAMPLE_SECONDS = 1.0f

    /** Por debajo de esta consistencia conviene repetir el registro (mucho ruido o frases muy cortas). */
    const val GOOD_CONSISTENCY = 0.6f

    fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm == 0f) vector.copyOf() else FloatArray(vector.size) { vector[it] / norm }
    }

    /** Parecido entre dos huellas: 1 = idénticas, 0 = nada que ver. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Huellas de distinto tamaño" }
        val na = normalize(a)
        val nb = normalize(b)
        var dot = 0f
        for (i in na.indices) dot += na[i] * nb[i]
        return dot
    }

    /** Huella del dueño: el promedio de las huellas de cada frase. */
    fun average(samples: List<FloatArray>): FloatArray {
        require(samples.isNotEmpty()) { "Se necesita al menos una frase" }
        val sum = FloatArray(samples.first().size)
        samples.map(::normalize).forEach { sample -> for (i in sum.indices) sum[i] += sample[i] }
        return normalize(sum)
    }

    /** Qué tanto se parecen las frases del registro entre sí (promedio contra la huella final). */
    fun consistency(samples: List<FloatArray>): Float {
        val profile = average(samples)
        return samples.map { similarity(it, profile) }.average().toFloat()
    }

    /** Convierte la huella en bytes (little endian) para cifrarla y guardarla. */
    fun toBytes(profile: FloatArray): ByteArray {
        val bytes = ByteArray(profile.size * 4)
        profile.forEachIndexed { i, value ->
            val bits = value.toRawBits()
            for (b in 0 until 4) bytes[i * 4 + b] = (bits shr (8 * b)).toByte()
        }
        return bytes
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "Huella dañada" }
        return FloatArray(bytes.size / 4) { i ->
            var bits = 0
            for (b in 0 until 4) bits = bits or ((bytes[i * 4 + b].toInt() and 0xFF) shl (8 * b))
            Float.fromBits(bits)
        }
    }
}
