package ec.mnexo.voiceprint

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Huella de voz cifrada con AES-256-GCM. La clave vive en el Android Keystore: está protegida
 * por el hardware del celular y no se puede copiar fuera de él. El archivo cifrado está en la
 * carpeta privada de la app y no se incluye en copias de seguridad (allowBackup=false).
 */
class KeystoreVoiceProfileStore(context: Context) : VoiceProfileStore {

    private val file = File(context.filesDir, "voiceprint.bin")

    override suspend fun load(): FloatArray? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        try {
            val data = file.readBytes()
            val ivLength = data[0].toInt()
            val iv = data.copyOfRange(1, 1 + ivLength)
            val encrypted = data.copyOfRange(1 + ivLength, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            VoicePrints.fromBytes(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            // Archivo dañado o clave perdida (por ejemplo, tras restablecer el celular): hay que registrar de nuevo.
            Log.w(TAG, "No se pudo leer la huella de voz; se descarta", e)
            file.delete()
            null
        }
    }

    override suspend fun save(profile: FloatArray) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(VoicePrints.toBytes(profile))
        val iv = cipher.iv
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + encrypted)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        file.delete()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        Unit
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "MnexoHuellaVoz"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mnexo_voiceprint"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
