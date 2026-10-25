package ec.mnexo.data.db

import androidx.room.Room
import ec.mnexo.conversation.ConversationStore
import java.io.File

/** Abre la base de datos en la carpeta de datos de la app (en Windows, %APPDATA%\MNEXO). */
fun createConversationStore(dataDir: File): ConversationStore {
    dataDir.mkdirs()
    val database = Room.databaseBuilder<MnexoDatabase>(name = File(dataDir, DATABASE_FILE_NAME).absolutePath)
        .buildMnexo()
    return RoomConversationStore(database.conversationDao())
}
