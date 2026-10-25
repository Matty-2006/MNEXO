package ec.mnexo.data.db

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ec.mnexo.conversation.ConversationStore
import ec.mnexo.conversation.ConversationTurn
import ec.mnexo.conversation.TurnRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tabla con los turnos de conversación. */
@Entity(tableName = "conversation_turn")
data class ConversationTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    /** "PERSONA" o "ASISTENTE". */
    val role: String,
    val text: String,
    val intent: String?,
)

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(turn: ConversationTurnEntity)

    @Query("SELECT * FROM conversation_turn ORDER BY id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ConversationTurnEntity>>

    /** Conserva solo los [keep] turnos más recientes. */
    @Query("DELETE FROM conversation_turn WHERE id NOT IN (SELECT id FROM conversation_turn ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM conversation_turn")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM conversation_turn")
    suspend fun count(): Int
}

/**
 * Base de datos local de MNEXO (SQLite mediante Room). Funciona igual en Android y en Windows.
 * Versión 1: historial de conversación. Las siguientes tablas (métricas, encuestas, archivos creados)
 * se agregarán con migraciones.
 */
@Database(entities = [ConversationTurnEntity::class], version = 1)
@ConstructedBy(MnexoDatabaseConstructor::class)
abstract class MnexoDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}

// Room genera la implementación de este objeto para cada plataforma.
@Suppress("KotlinNoActualForExpect")
expect object MnexoDatabaseConstructor : RoomDatabaseConstructor<MnexoDatabase> {
    override fun initialize(): MnexoDatabase
}

const val DATABASE_FILE_NAME = "mnexo.db"

/** Termina de configurar la base: SQLite incluido en la app y consultas fuera del hilo de la pantalla. */
internal fun RoomDatabase.Builder<MnexoDatabase>.buildMnexo(): MnexoDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Historial guardado en Room. Conserva como máximo [maxTurns] turnos para no acumular datos personales. */
class RoomConversationStore(
    private val dao: ConversationDao,
    private val maxTurns: Int = 200,
) : ConversationStore {

    override fun recent(limit: Int): Flow<List<ConversationTurn>> = dao.recent(limit).map { rows ->
        rows.reversed().map { row ->
            ConversationTurn(
                role = TurnRole.entries.firstOrNull { it.name == row.role } ?: TurnRole.ASISTENTE,
                text = row.text,
                intent = row.intent,
                timestampMs = row.timestampMs,
            )
        }
    }

    override suspend fun add(turn: ConversationTurn) {
        dao.insert(
            ConversationTurnEntity(
                timestampMs = turn.timestampMs,
                role = turn.role.name,
                text = turn.text,
                intent = turn.intent,
            ),
        )
        dao.trim(maxTurns)
    }

    override suspend fun clear() = dao.clear()
}
