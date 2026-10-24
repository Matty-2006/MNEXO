package ec.mnexo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import ec.mnexo.data.settings.TextSize

// Tema claro: texto casi negro sobre crema y azul profundo (contraste mayor a 7:1, nivel AAA de WCAG).
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B4F9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    background = Color(0xFFFFF8EE),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF0E6D8),
    onSurfaceVariant = Color(0xFF34343A),
    outline = Color(0xFF55555E),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

// Alto contraste: blanco y amarillo sobre negro, para baja visión.
private val HighContrastColors = darkColorScheme(
    primary = Color(0xFFFFD60A),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2B2400),
    onPrimaryContainer = Color(0xFFFFE680),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color.White,
    outline = Color(0xFFFFD60A),
    error = Color(0xFFFF8A80),
    onError = Color.Black,
)

// Tamaños base ya generosos; luego se multiplican por el tamaño de letra elegido.
private val BaseTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MnexoTheme(
    textSize: TextSize,
    highContrast: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Respeta el tamaño de letra del sistema y le suma la preferencia de la persona.
    val scaledDensity = Density(density.density, density.fontScale * textSize.scale)
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = if (highContrast) HighContrastColors else LightColors,
            typography = BaseTypography,
            content = content,
        )
    }
}
