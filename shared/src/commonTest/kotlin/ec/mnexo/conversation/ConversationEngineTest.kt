package ec.mnexo.conversation

import ec.mnexo.data.settings.TextSize
import ec.mnexo.data.settings.UserSettings
import ec.mnexo.understanding.RuleBasedUnderstander
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationEngineTest {

    // Sábado 24 de octubre de 2026, 15:25.
    private val fixedTime = TimeProvider { LocalDateTime(2026, 10, 24, 15, 25) }
    private val engine = ConversationEngine(RuleBasedUnderstander(), fixedTime)
    private val settings = UserSettings(userName = "Rosa", textSize = TextSize.GRANDE, onboardingCompleted = true)

    private fun respond(text: String, s: UserSettings = settings, context: ConversationContext = ConversationContext()) =
        engine.respond(text, s, context)

    @Test
    fun agrandaLaLetraYLoConfirma() {
        val (outcome, _) = respond("haz las letras más grandes")

        val action = assertIs<AppAction.UpdateSettings>(outcome.action)
        assertEquals(TextSize.MUY_GRANDE, action.settings.textSize)
        assertEquals("Listo, puse las letras más grandes.", outcome.reply)
    }

    @Test
    fun unPocoMasRepiteElUltimoAjuste() {
        val (_, context) = respond("letras más grandes")
        val (outcome, _) = respond("un poco más", settings.copy(textSize = TextSize.MUY_GRANDE), context)

        assertEquals(TextSize.ENORME, assertIs<AppAction.UpdateSettings>(outcome.action).settings.textSize)
    }

    @Test
    fun enElMaximoNoFingeQueCambioAlgo() {
        val (outcome, _) = respond("letras más grandes", settings.copy(textSize = TextSize.ENORME))

        assertEquals(AppAction.None, outcome.action)
        assertTrue(outcome.reply!!.contains("ya están en el tamaño más grande"))
    }

    @Test
    fun noEntendiRepiteMasDespacio() {
        assertEquals(AppAction.RepeatSlower, respond("no entendí").first.action)
    }

    @Test
    fun callateSeCallaSinResponder() {
        val (outcome, _) = respond("cállate")

        assertEquals(AppAction.StopSpeaking, outcome.action)
        assertEquals(null, outcome.reply)
    }

    @Test
    fun diceLaHoraYLaFechaComoUnaPersona() {
        assertEquals("Son las 3 y 25 de la tarde.", respond("¿qué hora es?").first.reply)
        assertEquals("Hoy es sábado 24 de octubre de 2026.", respond("¿qué día es hoy?").first.reply)
    }

    @Test
    fun loQueAunNoSabeHacerLoDiceConHonestidad() {
        val reply = respond("llama a mi hija María").first.reply!!

        assertTrue(reply.startsWith("Todavía no sé hacer llamadas"))
        assertTrue("mi hija maria" in reply, "Confirma lo que entendió")
    }

    @Test
    fun siNoEntiendeRepiteLoQueOyoYDaEjemplos() {
        val reply = respond("el perro de mi vecina ladra").first.reply!!

        assertTrue(reply.startsWith("No entendí «el perro de mi vecina ladra»"))
    }

    @Test
    fun saludaPorElNombre() {
        assertEquals("¡Hola, Rosa! ¿En qué te ayudo?", respond("buenos días").first.reply)
    }

    @Test
    fun laHoraSeDiceBien() {
        assertEquals("Es la 1 en punto de la tarde.", SpanishClock.time(LocalDateTime(2026, 1, 1, 13, 0)))
        assertEquals("Son las 12 y media de la madrugada.", SpanishClock.time(LocalDateTime(2026, 1, 1, 0, 30)))
        assertEquals("Son las 8 y cuarto de la mañana.", SpanishClock.time(LocalDateTime(2026, 1, 1, 8, 15)))
        assertEquals("Son las 9 y 5 de la noche.", SpanishClock.time(LocalDateTime(2026, 1, 1, 21, 5)))
    }
}
