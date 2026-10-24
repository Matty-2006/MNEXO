package ec.mnexo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MNEXO",
        // Ventana amplia desde el inicio para que el texto grande se lea sin redimensionar.
        state = rememberWindowState(width = 900.dp, height = 700.dp),
    ) {
        App()
    }
}
