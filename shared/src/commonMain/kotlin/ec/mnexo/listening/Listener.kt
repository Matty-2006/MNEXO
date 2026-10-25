package ec.mnexo.listening

import ec.mnexo.voiceprint.VoiceCheck
import kotlinx.coroutines.flow.StateFlow

enum class ListenProblem {
    /** No se pudo descargar el "oído" (sin internet, sin espacio o archivo alterado). */
    DOWNLOAD_FAILED,

    /** La persona no dio permiso para usar el micrófono. */
    PERMISSION_DENIED,

    /** No hay micrófono o lo está usando otra app. */
    NO_MICROPHONE,

    /** El motor de reconocimiento falló. Se puede volver a intentar. */
    ENGINE_ERROR,
}

sealed interface ListenerState {
    /** Falta descargar el modelo de reconocimiento (una sola vez). */
    data object NeedsModel : ListenerState

    /** Descargando el modelo; [progress] va de 0 a 1. */
    data class Downloading(val progress: Float) : ListenerState

    /** Cargando el modelo en memoria (solo la primera vez que se escucha). */
    data object Preparing : ListenerState

    data object Ready : ListenerState

    /** Micrófono abierto, esperando que la persona hable. */
    data object Listening : ListenerState

    /** La persona terminó de hablar; convirtiendo su voz en texto. */
    data object Recognizing : ListenerState

    data class Unavailable(val problem: ListenProblem) : ListenerState
}

sealed interface ListenResult {
    data class Heard(val text: String) : ListenResult

    /** Se entendió [text], pero la voz no parece la del dueño ([score] menor que el umbral). */
    data class UnknownSpeaker(val text: String, val score: Float) : ListenResult

    data object NothingHeard : ListenResult
    data class Failed(val problem: ListenProblem) : ListenResult
}

/** Resultado de grabar una frase para registrar la voz. */
sealed interface VoiceSampleResult {
    class Sample(val embedding: FloatArray, val seconds: Float) : VoiceSampleResult
    data object TooShort : VoiceSampleResult
    data object NothingHeard : VoiceSampleResult
    data class Failed(val problem: ListenProblem) : VoiceSampleResult
}

/**
 * Convierte la voz de la persona en texto, dentro del equipo y sin internet
 * (salvo la descarga única del modelo).
 */
interface Listener {
    val state: StateFlow<ListenerState>

    /** Tamaño de la descarga del modelo, para avisar a la persona antes de bajarlo. */
    val modelSizeMb: Long

    /** Descarga el modelo de reconocimiento si falta. */
    fun downloadModel()

    fun cancelDownload()

    /**
     * Escucha una frase y devuelve lo que entendió. Termina sola cuando la persona deja de hablar.
     * Si se da [voiceCheck], además comprueba que la voz sea la del dueño.
     * Cancelar la corrutina cierra el micrófono de inmediato.
     */
    suspend fun listen(voiceCheck: VoiceCheck? = null): ListenResult

    /** Graba una frase y devuelve su huella de voz, para registrar al dueño. */
    suspend fun recordVoiceSample(): VoiceSampleResult
}
