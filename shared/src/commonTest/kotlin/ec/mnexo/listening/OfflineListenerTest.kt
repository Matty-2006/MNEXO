package ec.mnexo.listening

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeStore(var installed: Boolean, private val failDownload: Boolean = false) : ModelStore {
    val progressReported = mutableListOf<Float>()
    override val isInstalled get() = installed
    override val totalBytes = 131_000_000L
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

private class FakeEngine(private val micProblem: ListenProblem? = null) : ListenEngine {
    override fun openMicrophone(): AudioInput {
        micProblem?.let { throw MicrophoneUnavailableException(it) }
        return object : AudioInput {
            private var reads = 0
            override fun start() {}
            override fun read(buffer: FloatArray): Int {
                buffer.fill(if (reads++ in 2..5) 1f else 0f)
                return buffer.size
            }
            override fun stop() {}
        }
    }

    override fun createVoiceActivityDetector() = object : VoiceActivityDetector {
        private var speaking = false
        private var done = false
        override fun accept(samples: FloatArray) {
            if (samples[0] > 0f) speaking = true else if (speaking) {
                speaking = false
                done = true
            }
        }
        override fun isSpeaking() = speaking
        override fun popSegment(): FloatArray? = if (done) FloatArray(1).also { done = false } else null
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
    fun sinPermisoDeMicrofonoLoExplica() = runTest {
        val listener = listener(FakeStore(installed = true), FakeEngine(micProblem = ListenProblem.PERMISSION_DENIED))

        assertEquals(ListenResult.Failed(ListenProblem.PERMISSION_DENIED), listener.listen())
        assertEquals(ListenerState.Unavailable(ListenProblem.PERMISSION_DENIED), listener.state.value)
    }
}
