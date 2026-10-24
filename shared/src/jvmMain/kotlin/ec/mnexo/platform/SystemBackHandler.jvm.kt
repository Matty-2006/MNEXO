package ec.mnexo.platform

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Windows no tiene botón "atrás" del sistema; se usan los botones "Volver" de cada pantalla.
}
