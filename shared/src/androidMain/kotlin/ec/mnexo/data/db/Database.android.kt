package ec.mnexo.data.db

import android.content.Context
import androidx.room.Room
import ec.mnexo.conversation.ConversationStore

/** Abre la base de datos en la carpeta privada de la app (no incluida en copias de seguridad). */
fun createConversationStore(context: Context): ConversationStore {
    val appContext = context.applicationContext
    val database = Room.databaseBuilder<MnexoDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
    ).buildMnexo()
    return RoomConversationStore(database.conversationDao())
}
