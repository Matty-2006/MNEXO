package ec.mnexo.voice

import kotlinx.coroutines.flow.StateFlow

/** Velocidad a la que habla el asistente. 1.0 es la velocidad normal del motor de voz. */
enum class SpeechRate(val multiplier: Float, val label: String) {
    MUY_LENTA(0.7f, "Muy lenta"),
    PAUSADA(0.85f, "Pausada"),
    NORMAL(1.0f, "Normal"),
    RAPIDA(1.15f, "Rápida");

    /** Un nivel más despacio; si ya es la más lenta, se queda igual. */
    fun slower(): SpeechRate = entries.getOrElse(ordinal - 1) { this }

    /** Un nivel más rápido; si ya es la más rápida, se queda igual. */
    fun faster(): SpeechRate = entries.getOrElse(ordinal + 1) { this }

    val isSlowest: Boolean get() = ordinal == 0

    companion object {
        fun fromStored(value: String?): SpeechRate = entries.firstOrNull { it.name == value } ?: PAUSADA
    }
}

/** Qué voz usar. */
enum class VoiceSource(val label: String) {
    /** La del equipo si tiene español; si no, la propia de MNEXO. */
    AUTOMATICA("Automática (recomendada)"),
    EQUIPO("La voz de este equipo"),
    MNEXO("La voz propia de MNEXO");

    companion object {
        fun fromStored(value: String?): VoiceSource = entries.firstOrNull { it.name == value } ?: AUTOMATICA
    }
}

enum class VoiceProblem {
    /** El equipo no tiene instalada una voz en español. */
    NO_SPANISH_VOICE,

    /** El motor de voz del sistema falló. Se puede volver a intentar. */
    ENGINE_ERROR,

    /** Sistema operativo sin voz implementada todavía (por ejemplo, Linux o macOS). */
    UNSUPPORTED_SYSTEM,
}

sealed interface SpeakerState {
    /** El motor de voz se está iniciando. */
    data object Preparing : SpeakerState
    data object Idle : SpeakerState
    data object Speaking : SpeakerState
    data class Unavailable(val problem: VoiceProblem) : SpeakerState
}

/**
 * Convierte texto en voz. Cada plataforma tiene su implementación:
 * Android usa TextToSpeech y Windows usa las voces del sistema.
 */
interface Speaker {
    val state: StateFlow<SpeakerState>

    /** Habla el texto; si ya estaba hablando, lo interrumpe y empieza con el nuevo. */
    fun speak(text: String, rate: SpeechRate)

    /** Se calla de inmediato. */
    fun stop()

    /** Abre la pantalla del sistema para instalar voces en español. Devuelve false si no pudo abrirla. */
    fun openVoiceSetup(): Boolean

    /** Qué voz prefiere la persona. Solo lo usa el altavoz combinado; los demás lo ignoran. */
    fun setPreferredSource(source: VoiceSource) {}
}
