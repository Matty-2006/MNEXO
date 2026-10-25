package ec.mnexo.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Altavoz combinado: usa la voz del equipo ([system]) o la voz propia de MNEXO ([own]).
 *
 * En modo automático prefiere la del equipo y, si no tiene español o falla, pasa a la propia.
 * Si la falla se descubre al intentar decir una frase, esa frase se repite con la voz propia:
 * la persona nunca se queda sin respuesta.
 */
class FallbackSpeaker(
    private val system: Speaker,
    private val own: Speaker,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Speaker {

    private class Request(val text: String, val rate: SpeechRate)

    @Volatile private var source = VoiceSource.AUTOMATICA

    /** Se vuelve false cuando la voz del equipo demuestra no servir (sin español o con fallas). */
    @Volatile private var systemUsable = true

    /** Última frase enviada a la voz del equipo, por si hay que repetirla con la propia. */
    @Volatile private var lastOnSystem: Pair<Request, TimeSource.Monotonic.ValueTimeMark>? = null

    private val _state = MutableStateFlow(system.state.value)
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(system.state, own.state) { systemState, ownState -> systemState to ownState }
                .collect { (systemState, ownState) ->
                    if (systemState is SpeakerState.Unavailable && source == VoiceSource.AUTOMATICA) {
                        systemUsable = false
                        retryWithOwnVoice()
                    }
                    _state.value = if (usesSystem()) systemState else ownState
                }
        }
    }

    override fun setPreferredSource(source: VoiceSource) {
        if (this.source == source) return
        this.source = source
        // Al cambiar de preferencia se vuelve a probar la voz del equipo.
        if (source != VoiceSource.MNEXO) systemUsable = true
        _state.value = if (usesSystem()) system.state.value else own.state.value
    }

    override fun speak(text: String, rate: SpeechRate) {
        stop()
        if (usesSystem()) {
            lastOnSystem = Request(text, rate) to TimeSource.Monotonic.markNow()
            system.speak(text, rate)
        } else {
            own.speak(text, rate)
        }
    }

    override fun stop() {
        lastOnSystem = null
        system.stop()
        own.stop()
    }

    override fun openVoiceSetup(): Boolean = system.openVoiceSetup()

    /** Repite con la voz propia la frase que la voz del equipo no pudo decir hace poco. */
    private fun retryWithOwnVoice() {
        val (request, sentAt) = lastOnSystem ?: return
        lastOnSystem = null
        if (sentAt.elapsedNow() <= RETRY_WINDOW) own.speak(request.text, request.rate)
    }

    private fun usesSystem(): Boolean = when (source) {
        VoiceSource.EQUIPO -> true
        VoiceSource.MNEXO -> false
        VoiceSource.AUTOMATICA -> systemUsable && system.state.value !is SpeakerState.Unavailable
    }

    private companion object {
        /** Una falla que llega después de este tiempo ya no se refiere a la última frase. */
        val RETRY_WINDOW = 30.seconds
    }
}
