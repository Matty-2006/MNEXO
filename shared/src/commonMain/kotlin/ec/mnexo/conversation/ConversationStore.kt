package ec.mnexo.conversation

import kotlinx.coroutines.flow.Flow

enum class TurnRole { PERSONA, ASISTENTE }

/** Un turno de la conversación: lo que dijo la persona o lo que respondió el asistente. */
data class ConversationTurn(
    val role: TurnRole,
    val text: String,
    /** Intención entendida (solo en turnos de la persona), útil para las métricas de la investigación. */
    val intent: String? = null,
    val timestampMs: Long = 0,
)

/** Historial de la conversación guardado en el equipo. Se conserva un número limitado de turnos. */
interface ConversationStore {
    /** Los turnos más recientes, del más antiguo al más nuevo. */
    fun recent(limit: Int): Flow<List<ConversationTurn>>

    suspend fun add(turn: ConversationTurn)

    suspend fun clear()
}
