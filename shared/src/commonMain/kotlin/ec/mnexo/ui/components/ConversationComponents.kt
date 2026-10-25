package ec.mnexo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ec.mnexo.conversation.ConversationTurn
import ec.mnexo.conversation.TurnRole

/** Últimos turnos de la conversación, como burbujas: lo que dijo la persona a la derecha, el asistente a la izquierda. */
@Composable
fun ConversationList(turns: List<ConversationTurn>, assistantName: String) {
    if (turns.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nuestra conversación",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        turns.forEach { turn ->
            val fromPerson = turn.role == TurnRole.PERSONA
            val who = if (fromPerson) "Tú" else assistantName
            Column(
                horizontalAlignment = if (fromPerson) Alignment.End else Alignment.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (fromPerson) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    // TalkBack lee primero quién habla.
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .semantics { contentDescription = "$who dijo: ${turn.text}" },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = who,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (fromPerson) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = turn.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (fromPerson) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Alternativa al micrófono: escribir lo que se necesita. */
@Composable
fun TypeToAssistant(onSend: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun send() {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 300) text = it },
            label = { Text("O escríbeme aquí", style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.isNotBlank()) BigSecondaryButton("Enviar", onClick = ::send)
    }
}
