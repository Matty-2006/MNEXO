package ec.mnexo.voiceprint

import ec.mnexo.listening.LISTEN_SAMPLE_RATE
import ec.mnexo.listening.SherpaDesktopListenEngine
import ec.mnexo.voice.SherpaDesktopSpeaker
import ec.mnexo.voice.SpeechRate
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pruebas reales en Windows: cifrado DPAPI y huella de voz con el modelo CAM++. */
class VoiceprintDesktopTest {

    private val temp: File = Files.createTempDirectory("mnexo-voiceprint").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun laHuellaSeGuardaCifradaYSeRecupera(): Unit = runBlocking {
        val file = File(temp, "voiceprint.bin")
        val store = DpapiVoiceProfileStore(file)
        val profile = FloatArray(192) { it / 192f }

        store.save(profile)

        val raw = file.readBytes()
        assertFalse(raw.asList().windowed(16).any { it == VoicePrints.toBytes(profile).take(16) }, "El archivo no debe tener la huella en claro")
        assertTrue(profile.contentEquals(assertNotNull(store.load())))

        store.delete()
        assertEquals(null, store.load())
    }

    @Test
    fun unArchivoAlteradoSeDescartaSinRomperLaApp(): Unit = runBlocking {
        val file = File(temp, "voiceprint.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        assertEquals(null, DpapiVoiceProfileStore(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun laMismaVozSeParaceMasQueUnaVozDistinta() {
        val engine = SherpaDesktopListenEngine(SherpaDesktopListenEngine.findVadFile())
        val embedder = engine.createSpeakerEmbedder(assertNotNull(System.getProperty("mnexo.asr.dir")))
        val voice = SherpaDesktopSpeaker(assertNotNull(SherpaDesktopSpeaker.findVoiceDir()))

        fun say(text: String, pitch: Float = 1f): FloatArray = runBlocking {
            val audio = voice.synthesize(text, SpeechRate.NORMAL)
            // Leer el audio a otra frecuencia cambia el tono y el timbre: suena como otra persona.
            resample(audio.samples, (audio.sampleRate * pitch).toInt(), LISTEN_SAMPLE_RATE)
        }

        val enrolled = VoicePrints.average(
            listOf(
                embedder.embed(say("Hola, soy yo y quiero usar mi asistente."), LISTEN_SAMPLE_RATE),
                embedder.embed(say("Mi familia es muy importante para mí."), LISTEN_SAMPLE_RATE),
            ),
        )
        val sameVoice = VoicePrints.similarity(embedder.embed(say("Por favor llama a mi hija esta tarde."), LISTEN_SAMPLE_RATE), enrolled)
        val otherVoice = VoicePrints.similarity(embedder.embed(say("Por favor llama a mi hija esta tarde.", pitch = 1.35f), LISTEN_SAMPLE_RATE), enrolled)

        println("Misma voz: %.2f | voz distinta: %.2f".format(sameVoice, otherVoice))
        assertTrue(sameVoice >= VoiceCheckLevel.NORMAL.threshold, "La misma voz debería pasar ($sameVoice)")
        assertTrue(otherVoice < sameVoice - 0.15f, "La voz distinta debería parecerse bastante menos ($otherVoice vs $sameVoice)")
    }

    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        val output = FloatArray((input.size.toLong() * to / from).toInt())
        for (i in output.indices) {
            val position = i.toDouble() * from / to
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            val a = input.getOrElse(index) { 0f }
            val b = input.getOrElse(index + 1) { a }
            output[i] = a + (b - a) * fraction
        }
        return output
    }
}
