package ec.mnexo.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Doble de prueba: anota lo que se le pide decir; su estado se controla desde la prueba. */
private class RecordingSpeaker(initial: SpeakerState = SpeakerState.Idle) : Speaker {
    val spoken = mutableListOf<String>()
    var stops = 0
    override val state = MutableStateFlow(initial)
    override fun speak(text: String, rate: SpeechRate) {
        spoken += text
    }
    override fun stop() {
        stops++
    }
    override fun openVoiceSetup() = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class FallbackSpeakerTest {

    private fun TestScope.fallback(system: Speaker, own: Speaker) =
        FallbackSpeaker(system, own, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun usaLaVozDelEquipoSiTieneEspanol() = runTest {
        val system = RecordingSpeaker()
        val own = RecordingSpeaker()

        fallback(system, own).speak("Hola", SpeechRate.NORMAL)

        assertEquals(listOf("Hola"), system.spoken)
        assertTrue(own.spoken.isEmpty())
    }

    @Test
    fun usaLaVozPropiaSiElEquipoNoTieneEspanol() = runTest {
        val system = RecordingSpeaker(SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE))
        val own = RecordingSpeaker()
        val speaker = fallback(system, own)

        speaker.speak("Hola", SpeechRate.NORMAL)

        assertEquals(listOf("Hola"), own.spoken)
        assertTrue(system.spoken.isEmpty())
        // La persona no ve el aviso de "falta la voz": MNEXO tiene la suya.
        assertEquals(SpeakerState.Idle, speaker.state.value)
    }

    @Test
    fun siLaVozDelEquipoFallaAlHablarRepiteLaFraseConLaPropia() = runTest {
        val system = RecordingSpeaker()
        val own = RecordingSpeaker()
        val speaker = fallback(system, own)

        speaker.speak("Hola, Rosa", SpeechRate.NORMAL)
        system.state.value = SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE)
        speaker.speak("Segunda frase", SpeechRate.NORMAL)

        assertEquals(listOf("Hola, Rosa", "Segunda frase"), own.spoken)
        assertEquals(listOf("Hola, Rosa"), system.spoken)
    }

    @Test
    fun conVozPropiaElegidaNuncaUsaLaDelEquipo() = runTest {
        val system = RecordingSpeaker()
        val own = RecordingSpeaker()
        val speaker = fallback(system, own)

        speaker.setPreferredSource(VoiceSource.MNEXO)
        speaker.speak("Hola", SpeechRate.NORMAL)

        assertEquals(listOf("Hola"), own.spoken)
        assertTrue(system.spoken.isEmpty())
    }

    @Test
    fun conVozDelEquipoElegidaMuestraSuProblemaSinCambiarDeVoz() = runTest {
        val system = RecordingSpeaker(SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE))
        val own = RecordingSpeaker()
        val speaker = fallback(system, own)

        speaker.setPreferredSource(VoiceSource.EQUIPO)
        speaker.speak("Hola", SpeechRate.NORMAL)

        assertTrue(own.spoken.isEmpty())
        assertEquals(SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE), speaker.state.value)
    }

    @Test
    fun detenerCallaAmbasVoces() = runTest {
        val system = RecordingSpeaker()
        val own = RecordingSpeaker()

        fallback(system, own).stop()

        assertEquals(1, system.stops)
        assertEquals(1, own.stops)
    }
}
