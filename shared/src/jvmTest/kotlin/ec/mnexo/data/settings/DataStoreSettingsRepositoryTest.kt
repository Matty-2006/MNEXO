package ec.mnexo.data.settings

import ec.mnexo.voice.SpeechRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Prueba con un archivo real en una carpeta temporal: guardar, leer y borrar. */
class DataStoreSettingsRepositoryTest {

    private val tempDir: File = Files.createTempDirectory("mnexo-test").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun newRepository() = DataStoreSettingsRepository(
        createSettingsDataStore(File(tempDir, SETTINGS_FILE_NAME).absolutePath, scope),
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun sinDatosGuardadosDevuelveLosValoresPorDefecto() = runBlocking {
        assertEquals(UserSettings(), newRepository().settings.first())
    }

    @Test
    fun guardaYLeeLasPreferenciasLimpiandoLosNombres() = runBlocking {
        val repository = newRepository()
        repository.save(
            UserSettings(
                userName = "  Rosa  ",
                assistantName = "",
                textSize = TextSize.ENORME,
                highContrast = true,
                speechRate = SpeechRate.MUY_LENTA,
                speakOnOpen = false,
                onboardingCompleted = true,
            ),
        )

        val saved = repository.settings.first()

        assertEquals(SpeechRate.MUY_LENTA, saved.speechRate)
        assertEquals(false, saved.speakOnOpen)

        assertEquals("Rosa", saved.userName)
        assertEquals(DEFAULT_ASSISTANT_NAME, saved.assistantName)
        assertEquals(TextSize.ENORME, saved.textSize)
        assertEquals(true, saved.highContrast)
        assertEquals(true, saved.onboardingCompleted)
    }

    @Test
    fun borrarMisDatosVuelveALaConfiguracionInicial() = runBlocking {
        val repository = newRepository()
        repository.save(UserSettings(userName = "Rosa", onboardingCompleted = true))

        repository.clear()

        assertEquals(UserSettings(), repository.settings.first())
    }
}
