package ec.mnexo.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.sayHello
import ec.mnexo.ui.components.BigButton
import ec.mnexo.ui.components.ScreenColumn
import ec.mnexo.ui.components.ScreenTitle

@Composable
fun HomeScreen(settings: UserSettings, onOpenSettings: () -> Unit) {
    ScreenColumn {
        ScreenTitle(sayHello(settings.userName))
        Text(
            text = "Soy ${settings.assistantName}. ¿Qué necesitas hacer?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // Aviso honesto: todavía no hay botón de voz para no mostrar algo que no funciona.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = "Muy pronto podrás hablarme y pedirme tareas. Por ahora puedes cambiar cómo me ves en Ajustes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        BigButton("Ajustes", onClick = onOpenSettings)
    }
}
