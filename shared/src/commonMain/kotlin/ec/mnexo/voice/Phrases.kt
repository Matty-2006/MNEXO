package ec.mnexo.voice

import ec.mnexo.data.settings.UserSettings
import ec.mnexo.listening.ListenProblem
import ec.mnexo.sayHello

/** Frases que dice el asistente. Cortas y claras. */
object Phrases {
    fun greeting(settings: UserSettings): String =
        "${sayHello(settings.userName)} Soy ${settings.assistantName}. ¿En qué te ayudo?"

    fun rateSample(rate: SpeechRate): String =
        "Así hablo a velocidad ${rate.label.lowercase()}. ¿Te parece bien?"

    fun sourceSample(source: VoiceSource): String = when (source) {
        VoiceSource.AUTOMATICA -> "Usaré la mejor voz disponible en este equipo."
        VoiceSource.EQUIPO -> "Esta es la voz de este equipo."
        VoiceSource.MNEXO -> "Esta es mi voz propia. Funciona en cualquier equipo, sin internet."
    }

    /** Confirmación verbal de lo entendido: la persona siempre sabe qué escuchó el asistente. */
    fun heard(text: String): String = "Entendí: $text"

    const val NOTHING_HEARD = "No te escuché. Toca Hablar e inténtalo otra vez."

    fun listenProblemMessage(problem: ListenProblem): String = when (problem) {
        ListenProblem.DOWNLOAD_FAILED ->
            "No pude descargar mi oído. Revisa tu conexión a internet e inténtalo otra vez."
        ListenProblem.PERMISSION_DENIED ->
            "Para escucharte necesito permiso para usar el micrófono. Puedes darlo en los ajustes del equipo."
        ListenProblem.NO_MICROPHONE ->
            "No pude usar el micrófono. Revisa que esté conectado y que otra app no lo esté usando."
        ListenProblem.ENGINE_ERROR ->
            "Tuve un problema para entenderte. Toca Hablar para intentarlo otra vez."
    }

    fun problemMessage(problem: VoiceProblem): String = when (problem) {
        VoiceProblem.NO_SPANISH_VOICE ->
            "Este equipo no tiene una voz en español instalada. Toca el botón para instalarla."
        VoiceProblem.ENGINE_ERROR ->
            "No pude usar la voz del equipo. Toca Escuchar para intentarlo otra vez."
        VoiceProblem.UNSUPPORTED_SYSTEM ->
            "Por ahora la voz solo funciona en Android y Windows."
    }
}
