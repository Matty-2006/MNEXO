package ec.mnexo.listening

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dónde vive el modelo de reconocimiento y cómo se descarga. */
interface ModelStore {
    val isInstalled: Boolean

    /** Tamaño total a descargar, en bytes. */
    val totalBytes: Long

    /** Carpeta con el modelo, para entregársela al motor. */
    val modelDir: String

    /** Descarga y verifica el modelo. [onProgress] recibe valores de 0 a 1. */
    suspend fun download(onProgress: (Float) -> Unit)
}

/** Lo que cada plataforma aporta: su micrófono y el motor sherpa-onnx en su versión. */
interface ListenEngine {
    fun openMicrophone(): AudioInput
    fun createVoiceActivityDetector(): VoiceActivityDetector
    fun createSpeechToText(modelDir: String): SpeechToText
}

/** "Oído" de MNEXO: descarga única del modelo, luego todo funciona sin internet. */
class OfflineListener(
    private val store: ModelStore,
    private val engine: ListenEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val timing: ListenTiming = ListenTiming(),
) : Listener {

    private val _state = MutableStateFlow<ListenerState>(
        if (store.isInstalled) ListenerState.Ready else ListenerState.NeedsModel,
    )
    override val state: StateFlow<ListenerState> = _state.asStateFlow()

    override val modelSizeMb: Long get() = store.totalBytes / 1_000_000

    private var downloadJob: Job? = null
    private val engineLock = Mutex()
    private var vad: VoiceActivityDetector? = null
    private var stt: SpeechToText? = null

    override fun downloadModel() {
        if (downloadJob?.isActive == true || store.isInstalled) return
        _state.value = ListenerState.Downloading(0f)
        downloadJob = scope.launch {
            try {
                store.download { progress -> _state.value = ListenerState.Downloading(progress) }
                _state.value = ListenerState.Ready
            } catch (e: CancellationException) {
                _state.value = ListenerState.NeedsModel
                throw e
            } catch (e: Exception) {
                _state.value = ListenerState.Unavailable(ListenProblem.DOWNLOAD_FAILED)
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
    }

    override suspend fun listen(): ListenResult {
        if (!store.isInstalled) return ListenResult.Failed(ListenProblem.DOWNLOAD_FAILED)

        val (detector, speechToText) = try {
            loadEngines()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return fail(ListenProblem.ENGINE_ERROR)
        }

        val microphone = try {
            engine.openMicrophone()
        } catch (e: MicrophoneUnavailableException) {
            return fail(e.problem)
        }

        _state.value = ListenerState.Listening
        return try {
            listenOnce(microphone, detector, speechToText, timing) { _state.value = ListenerState.Recognizing }
                .also { _state.value = ListenerState.Ready }
        } catch (e: MicrophoneUnavailableException) {
            fail(e.problem)
        } catch (e: CancellationException) {
            _state.value = ListenerState.Ready
            throw e
        } catch (e: Exception) {
            fail(ListenProblem.ENGINE_ERROR)
        }
    }

    private suspend fun loadEngines(): Pair<VoiceActivityDetector, SpeechToText> = engineLock.withLock {
        val loadedVad = vad
        val loadedStt = stt
        if (loadedVad != null && loadedStt != null) return@withLock loadedVad to loadedStt
        _state.value = ListenerState.Preparing
        val newVad = engine.createVoiceActivityDetector()
        val newStt = engine.createSpeechToText(store.modelDir)
        vad = newVad
        stt = newStt
        newVad to newStt
    }

    private fun fail(problem: ListenProblem): ListenResult {
        _state.value = ListenerState.Unavailable(problem)
        return ListenResult.Failed(problem)
    }
}
