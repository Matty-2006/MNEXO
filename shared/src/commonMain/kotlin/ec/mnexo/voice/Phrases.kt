package ec.mnexo.voice

import ec.mnexo.data.settings.UserSettings
import ec.mnexo.sayHello

/** Frases que dice el asistente. Cortas y claras. */
object Phrases {
    fun greeting(settings: UserSettings): String =
        "${sayHello(settings.userName)} Soy ${settings.assistantName}. ¿En qué te ayudo?"

    fun rateSample(rate: SpeechRate): String =
        "Así hablo a velocidad ${rate.label.lowercase()}. ¿Te parece bien?"

    fun problemMessage(problem: VoiceProblem): String = when (problem) {
        VoiceProblem.NO_SPANISH_VOICE ->
            "Este equipo no tiene una voz en español instalada. Toca el botón para instalarla."
        VoiceProblem.ENGINE_ERROR ->
            "No pude usar la voz del equipo. Toca Escuchar para intentarlo otra vez."
        VoiceProblem.UNSUPPORTED_SYSTEM ->
            "Por ahora la voz solo funciona en Android y Windows."
    }
}
