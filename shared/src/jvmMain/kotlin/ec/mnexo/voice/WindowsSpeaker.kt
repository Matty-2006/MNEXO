package ec.mnexo.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Base64
import java.util.Locale

/**
 * Voz de Windows usando las voces del sistema (Configuración → Hora e idioma → Voz).
 *
 * Java no trae voz en español, así que se usa la API de voz de Windows (WinRT) a través de PowerShell,
 * que viene instalado en todo Windows 10/11. Cada frase se dice en un proceso aparte:
 * para callarse basta con terminar ese proceso.
 *
 * Seguridad: el texto viaja codificado en Base64, nunca pegado tal cual dentro del comando,
 * así un texto con comillas o símbolos no puede convertirse en una orden para PowerShell.
 */
class WindowsSpeaker(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Speaker {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    private val _state = MutableStateFlow<SpeakerState>(
        if (isWindows) SpeakerState.Idle else SpeakerState.Unavailable(VoiceProblem.UNSUPPORTED_SYSTEM),
    )
    override val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private val lock = Any()
    private var current: Process? = null

    override fun speak(text: String, rate: SpeechRate) {
        if (!isWindows) return
        stop()
        val command = listOf(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", buildEncodedCommand(text.take(MAX_CHARS), rate),
        )
        scope.launch {
            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (e: IOException) {
                _state.value = SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
                return@launch
            }
            synchronized(lock) { current = process }
            _state.value = SpeakerState.Speaking

            // Leer la salida evita que el proceso se quede bloqueado esperando que alguien la lea.
            process.inputStream.use { it.readBytes() }
            val exitCode = process.waitFor()

            synchronized(lock) {
                // Si current ya no es este proceso, es porque stop() lo terminó a propósito.
                if (current === process) {
                    current = null
                    _state.value = when (exitCode) {
                        0 -> SpeakerState.Idle
                        EXIT_NO_SPANISH_VOICE -> SpeakerState.Unavailable(VoiceProblem.NO_SPANISH_VOICE)
                        else -> SpeakerState.Unavailable(VoiceProblem.ENGINE_ERROR)
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            current?.destroyForcibly()
            current = null
        }
        if (_state.value == SpeakerState.Speaking) _state.value = SpeakerState.Idle
    }

    override fun openVoiceSetup(): Boolean {
        if (!isWindows) return false
        return try {
            ProcessBuilder("explorer.exe", "ms-settings:speech").start()
            true
        } catch (e: IOException) {
            false
        }
    }

    internal companion object {
        const val MAX_CHARS = 1500
        const val EXIT_NO_SPANISH_VOICE = 3

        /** Script de PowerShell con dos marcadores que se reemplazan: velocidad y texto en Base64. */
        private val SCRIPT_TEMPLATE = """
            ${'$'}ErrorActionPreference = 'Stop'
            ${'$'}ProgressPreference = 'SilentlyContinue'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType = WindowsRuntime]
            ${'$'}voices = @([Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices)
            ${'$'}voice = ${'$'}null
            foreach (${'$'}lang in @('es-EC', 'es-MX', 'es-US', 'es-419', 'es-ES')) {
                ${'$'}voice = ${'$'}voices | Where-Object { ${'$'}_.Language -eq ${'$'}lang } | Select-Object -First 1
                if (${'$'}voice) { break }
            }
            if (-not ${'$'}voice) { ${'$'}voice = ${'$'}voices | Where-Object { ${'$'}_.Language -like 'es*' } | Select-Object -First 1 }
            if (-not ${'$'}voice) { exit $EXIT_NO_SPANISH_VOICE }
            ${'$'}synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
            ${'$'}synth.Voice = ${'$'}voice
            ${'$'}synth.Options.SpeakingRate = {{RATE}}
            ${'$'}text = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('{{TEXT}}'))
            ${'$'}asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and
                ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            } | Select-Object -First 1
            ${'$'}task = ${'$'}asTask.MakeGenericMethod([Windows.Media.SpeechSynthesis.SpeechSynthesisStream]).Invoke(${'$'}null, @(${'$'}synth.SynthesizeTextToStreamAsync(${'$'}text)))
            ${'$'}null = ${'$'}task.Wait(-1)
            ${'$'}stream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead(${'$'}task.Result)
            (New-Object System.Media.SoundPlayer(${'$'}stream)).PlaySync()
        """.trimIndent()

        /** Devuelve el script listo para "-EncodedCommand" (Base64 de UTF-16LE, como lo exige PowerShell). */
        fun buildEncodedCommand(text: String, rate: SpeechRate): String {
            val textBase64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
            val script = SCRIPT_TEMPLATE
                .replace("{{RATE}}", String.format(Locale.ROOT, "%.2f", rate.multiplier))
                .replace("{{TEXT}}", textBase64)
            return Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        }
    }
}
