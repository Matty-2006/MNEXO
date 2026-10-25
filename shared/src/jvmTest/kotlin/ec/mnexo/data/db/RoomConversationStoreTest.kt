package ec.mnexo.data.db

import ec.mnexo.conversation.ConversationTurn
import ec.mnexo.conversation.TurnRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Prueba con una base de datos SQLite real en una carpeta temporal. */
class RoomConversationStoreTest {

    private val temp: File = Files.createTempDirectory("mnexo-db").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun guardaYDevuelveLosTurnosEnOrden() = runBlocking {
        val store = createConversationStore(temp)
        store.add(ConversationTurn(TurnRole.PERSONA, "¿qué hora es?", "WHAT_TIME", 1))
        store.add(ConversationTurn(TurnRole.ASISTENTE, "Son las 3 de la tarde.", timestampMs = 2))

        val turns = store.recent(10).first()

        assertEquals(listOf("¿qué hora es?", "Son las 3 de la tarde."), turns.map { it.text })
        assertEquals(TurnRole.PERSONA, turns.first().role)
        assertEquals("WHAT_TIME", turns.first().intent)
    }

    @Test
    fun conservaSoloLosTurnosMasRecientes() = runBlocking {
        val store = createConversationStore(temp)
        repeat(205) { store.add(ConversationTurn(TurnRole.PERSONA, "frase $it", timestampMs = it.toLong())) }

        val all = store.recent(1_000).first()

        assertEquals(200, all.size)
        assertEquals("frase 204", all.last().text)
    }

    @Test
    fun borrarDejaElHistorialVacio() = runBlocking {
        val store = createConversationStore(temp)
        store.add(ConversationTurn(TurnRole.PERSONA, "hola", timestampMs = 1))

        store.clear()

        assertEquals(emptyList(), store.recent(10).first())
    }
}
