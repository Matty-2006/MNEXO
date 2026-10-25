package ec.mnexo.platform

import androidx.compose.runtime.Composable

/** Permiso para usar el micrófono. En Android se pide justo antes de escuchar; en Windows no hace falta. */
interface MicrophonePermission {
    val isGranted: Boolean

    /** Muestra el aviso del sistema y devuelve la respuesta en [onResult]. */
    fun request(onResult: (granted: Boolean) -> Unit)

    /** Abre los ajustes de la app, por si la persona negó el permiso y quiere darlo después. */
    fun openAppSettings()
}

@Composable
expect fun rememberMicrophonePermission(): MicrophonePermission
