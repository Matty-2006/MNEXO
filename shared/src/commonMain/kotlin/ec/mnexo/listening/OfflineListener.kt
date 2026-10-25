package ec.mnexo.listening

import ec.mnexo.voiceprint.SpeakerEmbedder
import ec.mnexo.voiceprint.VoiceCheck
import ec.mnexo.voiceprint.VoicePrints
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
import kotlinx.coroutines.withContext

/** Dónde viven los modelos del oído y cómo se descargan. */
interface ModelStore {
    val isInstalled: Boolean

    /** Lo que falta por descargar, en bytes (0 si ya está todo). */
    val remainingBytes: Long

    /** Carpeta con los modelos, para entregársela al motor. */
    val modelDir: String

    /** Descarga y verifica los modelos. [onProgress] recibe valores de 0 a 1. */
    suspend fun download(onProgress: (Float) -> Unit)
}

/** Lo que cada plataforma aporta: su micrófono y el motor sherpa-onnx en su versión. */
interface ListenEngine {
    fun openMicrophone(): AudioInput
    fun createVoiceActivityDetector(): VoiceActivityDetector
    fun createSpeechToText(modelDir: String): SpeechToText
    fun createSpeakerEmbedder(modelDir: String): SpeakerEmbedder
}

/** "Oído" de MNEXO: descarga única de los modelos, luego todo funciona sin internet. */
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

    override val modelSizeMb: Long get() = store.remainingBytes / 1_000_000

    private var downloadJob: Job? = null
    private val engineLock = Mutex()
    private var engines: Engines? = null

    private class Engines(val vad: VoiceActivityDetector, val stt: SpeechToText, val embedder: SpeakerEmbedder)

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

    override suspend fun listen(voiceCheck: VoiceCheck?): ListenResult {
        val loaded = prepare() ?: return failure()
        val segment = capture(loaded) ?: return lastResult ?: ListenResult.NothingHeard
        _state.value = ListenerState.Recognizing
        return try {
            withContext(Dispatchers.Default) {
                val text = loaded.stt.transcribe(segment, LISTEN_SAMPLE_RATE).trim()
                when {
                    text.isEmpty() -> ListenResult.NothingHeard
                    voiceCheck == null -> ListenResult.Heard(text)
                    else -> {
                        val score = VoicePrints.similarity(loaded.embedder.embed(segment, LISTEN_SAMPLE_RATE), voiceCheck.profile)
                        if (score >= voiceCheck.threshold) ListenResult.Heard(text) else ListenResult.UnknownSpeaker(text, score)
                    }
                }
            }.also { _state.value = ListenerState.Ready }
        } catch (e: CancellationException) {
            _state.value = ListenerState.Ready
            throw e
        } catch (e: Exception) {
            fail(ListenProblem.ENGINE_ERROR)
        }
    }

    override suspend fun recordVoiceSample(): VoiceSampleResult {
        val loaded = prepare() ?: return VoiceSampleResult.Failed(problemOf(lastResult))
        val segment = capture(loaded) ?: return when (val result = lastResult) {
            is ListenResult.Failed -> VoiceSampleResult.Failed(result.problem)
            else -> VoiceSampleResult.NothingHeard
        }
        val seconds = segment.size.toFloat() / LISTEN_SAMPLE_RATE
        if (seconds < VoicePrints.MIN_SAMPLE_SECONDS) {
            _state.value = ListenerState.Ready
            return VoiceSampleResult.TooShort
        }
        _state.value = ListenerState.Recognizing
        return try {
            val embedding = withContext(Dispatchers.Default) { loaded.embedder.embed(segment, LISTEN_SAMPLE_RATE) }
            _state.value = ListenerState.Ready
            VoiceSampleResult.Sample(embedding, seconds)
        } catch (e: CancellationException) {
            _state.value = ListenerState.Ready
            throw e
        } catch (e: Exception) {
            fail(ListenProblem.ENGINE_ERROR)
            VoiceSampleResult.Failed(ListenProblem.ENGINE_ERROR)
        }
    }

    /** Resultado de error del último intento de preparar o capturar (si lo hubo). */
    private var lastResult: ListenResult? = null

    private suspend fun prepare(): Engines? {
        lastResult = null
        if (!store.isInstalled) {
            lastResult = ListenResult.Failed(ListenProblem.DOWNLOAD_FAILED)
            return null
        }
        return try {
            loadEngines()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastResult = fail(ListenProblem.ENGINE_ERROR)
            null
        }
    }

    /** Abre el micrófono y captura una frase; null si no se oyó nada o si falló (ver [lastResult]). */
    private suspend fun capture(loaded: Engines): FloatArray? {
        val microphone = try {
            engine.openMicrophone()
        } catch (e: MicrophoneUnavailableException) {
            lastResult = fail(e.problem)
            return null
        }
        _state.value = ListenerState.Listening
        return try {
            captureUtterance(microphone, loaded.vad, timing).also { if (it == null) _state.value = ListenerState.Ready }
        } catch (e: MicrophoneUnavailableException) {
            lastResult = fail(e.problem)
            null
        } catch (e: CancellationException) {
            _state.value = ListenerState.Ready
            throw e
        } catch (e: Exception) {
            lastResult = fail(ListenProblem.ENGINE_ERROR)
            null
        }
    }

    private suspend fun loadEngines(): Engines = engineLock.withLock {
        engines ?: run {
            _state.value = ListenerState.Preparing
            Engines(
                vad = engine.createVoiceActivityDetector(),
                stt = engine.createSpeechToText(store.modelDir),
                embedder = engine.createSpeakerEmbedder(store.modelDir),
            ).also { engines = it }
        }
    }

    private fun failure(): ListenResult = lastResult ?: ListenResult.Failed(ListenProblem.ENGINE_ERROR)

    private fun problemOf(result: ListenResult?): ListenProblem =
        (result as? ListenResult.Failed)?.problem ?: ListenProblem.ENGINE_ERROR

    private fun fail(problem: ListenProblem): ListenResult {
        _state.value = ListenerState.Unavailable(problem)
        return ListenResult.Failed(problem)
    }
}
