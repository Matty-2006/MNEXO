package ec.mnexo.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.IOException

@Composable
actual fun rememberMicrophonePermission(): MicrophonePermission = remember {
    // En Windows las apps de escritorio no piden permiso por cada uso; se controla en
    // Configuración > Privacidad > Micrófono ("Permitir que las aplicaciones de escritorio...").
    object : MicrophonePermission {
        override val isGranted = true

        override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)

        override fun openAppSettings() {
            try {
                ProcessBuilder("explorer.exe", "ms-settings:privacy-microphone").start()
            } catch (e: IOException) {
                // Sin Windows (por ejemplo, Linux) no hay pantalla equivalente.
            }
        }
    }
}
