package ec.mnexo.presentation

import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.voice.Phrases
import ec.mnexo.voice.Speaker
import ec.mnexo.voice.SpeakerState
import ec.mnexo.voice.SpeechRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Doble de prueba: guarda en memoria en lugar de un archivo. */
private class FakeSettingsRepository(initial: UserSettings) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun save(settings: UserSettings) {
        this.settings.value = settings
    }
    override suspend fun clear() {
        settings.value = UserSettings()
    }
}

/** Doble de prueba: anota lo que se le pidió decir en lugar de sonar. */
private class FakeSpeaker : Speaker {
    val spoken = mutableListOf<Pair<String, SpeechRate>>()
    var stopCalls = 0
    override val state: StateFlow<SpeakerState> = MutableStateFlow(SpeakerState.Idle)
    override fun speak(text: String, rate: SpeechRate) {
        spoken += text to rate
    }
    override fun stop() {
        stopCalls++
    }
    override fun openVoiceSetup() = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val ready = UserSettings(userName = "Rosa", onboardingCompleted = true, speechRate = SpeechRate.NORMAL)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saludaUnaSolaVezAunqueSeLlameVariasVeces() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.greetIfNeeded()
        viewModel.greetIfNeeded()

        assertEquals(listOf(Phrases.greeting(ready) to SpeechRate.NORMAL), speaker.spoken)
    }

    @Test
    fun noSaludaSiLaPersonaLoDesactivo() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready.copy(speakOnOpen = false)), speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.greetIfNeeded()

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun masDespacioRepiteLaUltimaFraseYGuardaLaNuevaVelocidad() = runTest {
        val repository = FakeSettingsRepository(ready)
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(repository, speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.speakGreeting()
        viewModel.repeatSlower()

        assertEquals(Phrases.greeting(ready) to SpeechRate.PAUSADA, speaker.spoken.last())
        assertEquals(SpeechRate.PAUSADA, repository.settings.value.speechRate)
    }

    @Test
    fun repetirUsaLaMismaFraseYVelocidad() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.speakGreeting()
        viewModel.repeatLast()

        assertEquals(2, speaker.spoken.size)
        assertEquals(speaker.spoken[0], speaker.spoken[1])
    }

    @Test
    fun probarUnaVelocidadNoLaGuarda() = runTest {
        val repository = FakeSettingsRepository(ready)
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(repository, speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.previewRate(SpeechRate.MUY_LENTA)

        assertEquals(SpeechRate.MUY_LENTA, speaker.spoken.single().second)
        assertEquals(SpeechRate.NORMAL, repository.settings.value.speechRate)
    }

    @Test
    fun borrarMisDatosCallaAlAsistente() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.deleteAllData()

        assertTrue(speaker.stopCalls > 0)
        assertEquals(null, viewModel.lastSpoken.value)
    }
}
