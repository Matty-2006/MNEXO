package ec.mnexo.platform

import androidx.compose.runtime.Composable

/**
 * Reacciona al botón o gesto "atrás" del sistema.
 * En Android usa el botón atrás; en Windows no hay equivalente, así que no hace nada
 * (las pantallas siempre muestran un botón "Volver" visible).
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
