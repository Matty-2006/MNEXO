package ec.mnexo.presentation

import ec.mnexo.data.settings.SettingsRepository
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.listening.ListenResult
import ec.mnexo.listening.Listener
import ec.mnexo.listening.ListenerState
import ec.mnexo.listening.VoiceSampleResult
import ec.mnexo.voiceprint.VoiceCheck
import ec.mnexo.voiceprint.VoicePrints
import ec.mnexo.voiceprint.VoiceProfileStore
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

/** Doble de prueba: devuelve el resultado que la prueba le indique y anota si se pidió comprobar la voz. */
private class FakeListener(
    private val result: ListenResult = ListenResult.NothingHeard,
    private val sample: VoiceSampleResult = VoiceSampleResult.Sample(floatArrayOf(1f, 0f), 2f),
) : Listener {
    var lastVoiceCheck: VoiceCheck? = null
    override val state = MutableStateFlow<ListenerState>(ListenerState.Ready)
    override val modelSizeMb = 131L
    override fun downloadModel() {}
    override fun cancelDownload() {}
    override suspend fun listen(voiceCheck: VoiceCheck?): ListenResult {
        lastVoiceCheck = voiceCheck
        return result
    }
    override suspend fun recordVoiceSample() = sample
}

/** Doble de prueba: guarda la huella en memoria. */
private class FakeProfileStore(var profile: FloatArray? = null) : VoiceProfileStore {
    override suspend fun load() = profile
    override suspend fun save(profile: FloatArray) {
        this.profile = profile
    }
    override suspend fun delete() {
        profile = null
    }
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
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker, FakeListener(), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.greetIfNeeded()
        viewModel.greetIfNeeded()

        assertEquals(listOf(Phrases.greeting(ready) to SpeechRate.NORMAL), speaker.spoken)
    }

    @Test
    fun noSaludaSiLaPersonaLoDesactivo() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready.copy(speakOnOpen = false)), speaker, FakeListener(), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.greetIfNeeded()

        assertTrue(speaker.spoken.isEmpty())
    }

    @Test
    fun masDespacioRepiteLaUltimaFraseYGuardaLaNuevaVelocidad() = runTest {
        val repository = FakeSettingsRepository(ready)
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(repository, speaker, FakeListener(), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.speakGreeting()
        viewModel.repeatSlower()

        assertEquals(Phrases.greeting(ready) to SpeechRate.PAUSADA, speaker.spoken.last())
        assertEquals(SpeechRate.PAUSADA, repository.settings.value.speechRate)
    }

    @Test
    fun repetirUsaLaMismaFraseYVelocidad() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker, FakeListener(), FakeProfileStore())
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
        val viewModel = AppViewModel(repository, speaker, FakeListener(), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.previewRate(SpeechRate.MUY_LENTA)

        assertEquals(SpeechRate.MUY_LENTA, speaker.spoken.single().second)
        assertEquals(SpeechRate.NORMAL, repository.settings.value.speechRate)
    }

    @Test
    fun alEscucharSeCallaYConfirmaEnVozAltaLoQueEntendio() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker, FakeListener(ListenResult.Heard("llama a María")), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startListening()

        assertTrue(speaker.stopCalls > 0, "Debe callarse antes de escuchar para no oírse a sí mismo")
        assertEquals("llama a María", viewModel.heard.value)
        assertEquals("Entendí: llama a María", speaker.spoken.last().first)
    }

    @Test
    fun siNoEscuchaNadaLoDiceConCalma() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker, FakeListener(ListenResult.NothingHeard), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startListening()

        assertEquals(Phrases.NOTHING_HEARD, speaker.spoken.last().first)
        assertEquals(null, viewModel.heard.value)
    }

    @Test
    fun conHuellaRegistradaPideComprobarLaVozDelDueno() = runTest {
        val listener = FakeListener(ListenResult.Heard("hola"))
        val viewModel = AppViewModel(FakeSettingsRepository(ready), FakeSpeaker(), listener, FakeProfileStore(floatArrayOf(1f, 0f)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startListening()

        assertEquals(0.55f, listener.lastVoiceCheck?.threshold)
    }

    @Test
    fun siLaPersonaDesactivaSoloMiVozNoSeComprueba() = runTest {
        val listener = FakeListener(ListenResult.Heard("hola"))
        val viewModel = AppViewModel(
            FakeSettingsRepository(ready.copy(ownerVoiceOnly = false)), FakeSpeaker(), listener, FakeProfileStore(floatArrayOf(1f, 0f)),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startListening()

        assertEquals(null, listener.lastVoiceCheck)
    }

    @Test
    fun conOtraVozNoRepiteLoQueDijoYEsperaSiSoyYo() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(
            FakeSettingsRepository(ready), speaker, FakeListener(ListenResult.UnknownSpeaker("compra algo", 0.2f)), FakeProfileStore(floatArrayOf(1f, 0f)),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startListening()

        assertEquals(Phrases.UNKNOWN_SPEAKER, speaker.spoken.last().first)
        assertEquals(null, viewModel.heard.value, "No se acepta hasta que confirme")

        viewModel.confirmUnknownSpeakerIsOwner()

        assertEquals("compra algo", viewModel.heard.value)
        assertEquals(null, viewModel.pendingUnknown.value)
    }

    @Test
    fun registrarLaVozGuardaLaHuellaTrasCincoFrases() = runTest {
        val store = FakeProfileStore()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), FakeSpeaker(), FakeListener(), store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startEnrollment()
        repeat(VoicePrints.ENROLLMENT_PHRASES.size - 1) { viewModel.recordEnrollmentPhrase() }

        assertEquals(null, store.profile, "Con 4 frases todavía no se guarda")
        assertEquals(4, viewModel.enrollment.value?.phraseIndex)

        viewModel.recordEnrollmentPhrase()

        assertTrue(viewModel.enrollment.value?.finished == true)
        assertTrue(viewModel.hasVoiceProfile.value)
        assertTrue(store.profile != null)
    }

    @Test
    fun unaFraseCortaNoAvanzaElRegistro() = runTest {
        val viewModel = AppViewModel(
            FakeSettingsRepository(ready), FakeSpeaker(), FakeListener(sample = VoiceSampleResult.TooShort), FakeProfileStore(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.startEnrollment()
        viewModel.recordEnrollmentPhrase()

        assertEquals(0, viewModel.enrollment.value?.phraseIndex)
        assertTrue(viewModel.enrollment.value?.message?.contains("corta") == true)
    }

    @Test
    fun borrarMisDatosTambienBorraLaHuellaDeVoz() = runTest {
        val store = FakeProfileStore(floatArrayOf(1f, 0f))
        val viewModel = AppViewModel(FakeSettingsRepository(ready), FakeSpeaker(), FakeListener(), store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.deleteAllData()

        assertEquals(null, store.profile)
        assertEquals(false, viewModel.hasVoiceProfile.value)
    }

    @Test
    fun borrarMisDatosCallaAlAsistente() = runTest {
        val speaker = FakeSpeaker()
        val viewModel = AppViewModel(FakeSettingsRepository(ready), speaker, FakeListener(), FakeProfileStore())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.deleteAllData()

        assertTrue(speaker.stopCalls > 0)
        assertEquals(null, viewModel.lastSpoken.value)
    }
}
