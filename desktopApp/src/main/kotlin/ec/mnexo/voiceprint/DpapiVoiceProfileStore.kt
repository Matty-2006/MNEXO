package ec.mnexo.voiceprint

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Huella de voz cifrada con DPAPI, el cifrado de Windows ligado a la cuenta del usuario:
 * solo esa persona, en ese equipo, puede descifrarla. Ni otro usuario ni otra PC pueden leerla.
 */
class DpapiVoiceProfileStore(private val file: File) : VoiceProfileStore {

    override suspend fun load(): FloatArray? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        try {
            VoicePrints.fromBytes(Crypt32Util.cryptUnprotectData(file.readBytes()))
        } catch (e: Exception) {
            // Archivo dañado o de otro usuario: hay que registrar la voz de nuevo.
            System.err.println("MNEXO: no se pudo leer la huella de voz; se descarta: $e")
            file.delete()
            null
        }
    }

    override suspend fun save(profile: FloatArray) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(Crypt32Util.cryptProtectData(VoicePrints.toBytes(profile)))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }
}
