package ec.mnexo.listening

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ec.mnexo.voiceprint.SpeakerEmbedder
import ec.mnexo.voiceprint.VoiceCheck
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeStore(var installed: Boolean, private val failDownload: Boolean = false) : ModelStore {
    val progressReported = mutableListOf<Float>()
    override val isInstalled get() = installed
    override val remainingBytes get() = if (installed) 0L else 131_000_000L
    override val modelDir = "/modelo"
    override suspend fun download(onProgress: (Float) -> Unit) {
        if (failDownload) throw IOException("sin internet")
        listOf(0.5f, 1f).forEach {
            progressReported += it
            onProgress(it)
        }
        installed = true
    }
}

/**
 * Motor simulado. El micrófono "oye" [speechWindows] ventanas de voz; la huella de voz que
 * devuelve es [voice] (así se simula a la misma persona o a otra).
 */
private class FakeEngine(
    private val micProblem: ListenProblem? = null,
    private val speechWindows: Int = 4,
    private val voice: FloatArray = floatArrayOf(1f, 0f),
) : ListenEngine {
    override fun openMicrophone(): AudioInput {
        micProblem?.let { throw MicrophoneUnavailableException(it) }
        return object : AudioInput {
            private var reads = 0
            override fun start() {}
            override fun read(buffer: FloatArray): Int {
                buffer.fill(if (reads++ in 2 until 2 + speechWindows) 1f else 0f)
                return buffer.size
            }
            override fun stop() {}
        }
    }

    override fun createSpeakerEmbedder(modelDir: String) = SpeakerEmbedder { _, _ -> voice }

    override fun createVoiceActivityDetector() = object : VoiceActivityDetector {
        private var speaking = false
        private var done = false
        private var length = 0
        override fun accept(samples: FloatArray) {
            if (samples[0] > 0f) {
                speaking = true
                length += samples.size
            } else if (speaking) {
                speaking = false
                done = true
            }
        }
        override fun isSpeaking() = speaking
        override fun popSegment(): SpeechSegment? =
            if (done) SpeechSegment(2 * VAD_WINDOW, FloatArray(length)).also { done = false; length = 0 } else null
        override fun flush() {}
        override fun reset() {}
    }

    override fun createSpeechToText(modelDir: String) = SpeechToText { _, _ -> "hola" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineListenerTest {

    private fun TestScope.listener(store: ModelStore, engine: ListenEngine = FakeEngine()) =
        OfflineListener(store, engine, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun sinModeloPideDescargarlo() = runTest {
        assertEquals(ListenerState.NeedsModel, listener(FakeStore(installed = false)).state.value)
        assertEquals(131L, listener(FakeStore(installed = false)).modelSizeMb)
        assertEquals(0L, listener(FakeStore(installed = true)).modelSizeMb)
    }

    @Test
    fun laDescargaInformaElProgresoYTerminaLista() = runTest {
        val store = FakeStore(installed = false)
        val listener = listener(store)

        listener.downloadModel()

        assertEquals(listOf(0.5f, 1f), store.progressReported)
        assertEquals(ListenerState.Ready, listener.state.value)
    }

    @Test
    fun siLaDescargaFallaLoDiceYPermiteReintentar() = runTest {
        val listener = listener(FakeStore(installed = false, failDownload = true))

        listener.downloadModel()

        assertEquals(ListenerState.Unavailable(ListenProblem.DOWNLOAD_FAILED), listener.state.value)
    }

    @Test
    fun conModeloEscuchaYDevuelveElTexto() = runTest {
        val listener = listener(FakeStore(installed = true))

        assertEquals(ListenResult.Heard("hola"), listener.listen())
        assertEquals(ListenerState.Ready, listener.state.value)
    }

    @Test
    fun conLaVozDelDuenoObedece() = runTest {
        val owner = VoiceCheck(profile = floatArrayOf(1f, 0f), threshold = 0.55f)

        assertEquals(ListenResult.Heard("hola"), listener(FakeStore(installed = true)).listen(owner))
    }

    @Test
    fun conOtraVozNoObedeceYPregunta() = runTest {
        val owner = VoiceCheck(profile = floatArrayOf(1f, 0f), threshold = 0.55f)
        val stranger = FakeEngine(voice = floatArrayOf(0f, 1f))

        val result = listener(FakeStore(installed = true), stranger).listen(owner)

        val unknown = assertIs<ListenResult.UnknownSpeaker>(result)
        assertEquals("hola", unknown.text)
        assertTrue(unknown.score < 0.55f)
    }

    @Test
    fun grabaUnaFraseParaRegistrarLaVoz() = runTest {
        // 40 ventanas de 512 muestras ≈ 1,3 s: suficiente para una huella.
        val listener = listener(FakeStore(installed = true), FakeEngine(speechWindows = 40, voice = floatArrayOf(0.6f, 0.8f)))

        val sample = assertIs<VoiceSampleResult.Sample>(listener.recordVoiceSample())

        assertTrue(sample.embedding.contentEquals(floatArrayOf(0.6f, 0.8f)))
        assertTrue(sample.seconds >= 1f)
    }

    @Test
    fun unaFraseDemasiadoCortaNoSirveParaRegistrar() = runTest {
        val listener = listener(FakeStore(installed = true), FakeEngine(speechWindows = 3))

        assertEquals(VoiceSampleResult.TooShort, listener.recordVoiceSample())
    }

    @Test
    fun sinPermisoDeMicrofonoLoExplica() = runTest {
        val listener = listener(FakeStore(installed = true), FakeEngine(micProblem = ListenProblem.PERMISSION_DENIED))

        assertEquals(ListenResult.Failed(ListenProblem.PERMISSION_DENIED), listener.listen())
        assertEquals(ListenerState.Unavailable(ListenProblem.PERMISSION_DENIED), listener.state.value)
    }
}
