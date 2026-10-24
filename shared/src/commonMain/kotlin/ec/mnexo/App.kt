package ec.mnexo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores provisionales con alto contraste (texto casi negro sobre fondo crema).
// El tema accesible completo llega en el paso 1.1.
private val Fondo = Color(0xFFFFF8EE)
private val TextoPrincipal = Color(0xFF1B1B1F)
private val TextoSecundario = Color(0xFF44474E)

/**
 * Pantalla compartida por Android y Windows.
 * Es el mismo código para las dos plataformas: solo cambia el nombre del sistema que se muestra abajo.
 */
@Composable
@Preview
fun App() {
    MaterialTheme {
        val saludo = remember { Greeting().greet() }
        val sistema = remember { getPlatform().name }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Fondo)
                .safeContentPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = saludo,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = TextoPrincipal,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "¿Qué necesitas hacer?",
                fontSize = 28.sp,
                color = TextoPrincipal,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Funcionando en $sistema",
                fontSize = 20.sp,
                color = TextoSecundario,
                textAlign = TextAlign.Center,
            )
        }
    }
}
