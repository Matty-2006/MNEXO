package ec.mnexo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ec.mnexo.data.settings.MAX_NAME_LENGTH

private val BigShape = RoundedCornerShape(20.dp)

/**
 * Contenedor de cada pantalla: ocupa todo, se puede desplazar (con letra enorme el contenido
 * no cabe) y en pantallas anchas como Windows limita el ancho para que las líneas sean legibles.
 */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

/** Título de pantalla. TalkBack lo anuncia como encabezado. */
@Composable
fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/** Botón principal: ancho completo y al menos 72 dp de alto, fácil de tocar. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = BigShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Botón secundario, con borde grueso para que se distinga bien. */
@Composable
fun BigSecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = BigShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** Campo para escribir un nombre. Pone mayúscula inicial y "Listo" en el teclado ejecuta [onDone]. */
@Composable
fun NameField(value: String, onValueChange: (String) -> Unit, label: String, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= MAX_NAME_LENGTH) onValueChange(it) },
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Opción de una lista donde solo se elige una (TalkBack la anuncia como "botón de opción"). */
@Composable
fun BigRadioOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        shape = BigShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // El clic lo maneja toda la fila, por eso el círculo no recibe onClick.
            RadioButton(selected = selected, onClick = null)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** Interruptor de sí/no; toda la fila es tocable, no solo el interruptor. */
@Composable
fun BigSwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        shape = BigShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
