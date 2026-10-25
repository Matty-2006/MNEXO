package ec.mnexo.understanding

import ec.mnexo.understanding.IntentType.CALL
import ec.mnexo.understanding.IntentType.CONTRAST_OFF
import ec.mnexo.understanding.IntentType.CONTRAST_ON
import ec.mnexo.understanding.IntentType.CREATE_DOCUMENT
import ec.mnexo.understanding.IntentType.CREATE_SURVEY
import ec.mnexo.understanding.IntentType.GREETING
import ec.mnexo.understanding.IntentType.HELP
import ec.mnexo.understanding.IntentType.NO
import ec.mnexo.understanding.IntentType.OPEN_SETTINGS
import ec.mnexo.understanding.IntentType.ORDER_FOOD
import ec.mnexo.understanding.IntentType.REPEAT
import ec.mnexo.understanding.IntentType.REPEAT_SLOWER
import ec.mnexo.understanding.IntentType.SEND_MESSAGE
import ec.mnexo.understanding.IntentType.SPEAK_FASTER
import ec.mnexo.understanding.IntentType.STOP
import ec.mnexo.understanding.IntentType.TEXT_BIGGER
import ec.mnexo.understanding.IntentType.TEXT_SMALLER
import ec.mnexo.understanding.IntentType.THANKS
import ec.mnexo.understanding.IntentType.UNKNOWN
import ec.mnexo.understanding.IntentType.WHAT_DATE
import ec.mnexo.understanding.IntentType.WHAT_TIME
import ec.mnexo.understanding.IntentType.WHO_ARE_YOU
import ec.mnexo.understanding.IntentType.YES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleBasedUnderstanderTest {

    private val understander = RuleBasedUnderstander { "Lucía" }

    /**
     * Banco de frases: cómo podría pedir cada cosa una persona mayor en Ecuador, incluidos errores
     * típicos del reconocimiento de voz (sin tildes, palabras pegadas o mal escritas).
     */
    private val bank: List<Pair<String, IntentType>> = listOf(
        // Repetir
        "repite" to REPEAT,
        "Lucía, repítemelo por favor" to REPEAT,
        "¿qué dijiste?" to REPEAT,
        "¿mande?" to REPEAT,
        "¿cómo?" to REPEAT,
        "dilo otra vez" to REPEAT,
        "no te escuché" to REPEAT,
        "repitelo" to REPEAT,
        // Más despacio
        "no entendí" to REPEAT_SLOWER,
        "no te entendí nada, más despacio" to REPEAT_SLOWER,
        "habla más despacito" to REPEAT_SLOWER,
        "más lento por favor" to REPEAT_SLOWER,
        "explícamelo más despacio" to REPEAT_SLOWER,
        // Más rápido
        "habla más rápido" to SPEAK_FASTER,
        "más ligero" to SPEAK_FASTER,
        // Detener
        "para" to STOP,
        "cállate" to STOP,
        "ya, basta" to STOP,
        "deja de hablar por favor" to STOP,
        // Letra
        "haz las letras más grandes" to TEXT_BIGGER,
        "pon la letra más grande" to TEXT_BIGGER,
        "agranda las letras" to TEXT_BIGGER,
        "no alcanzo a leer" to TEXT_BIGGER,
        "no veo bien" to TEXT_BIGGER,
        "las letritas están muy pequeñas" to TEXT_BIGGER,
        "letras mas grandes" to TEXT_BIGGER,
        "pon las letras más pequeñas" to TEXT_SMALLER,
        "achica el texto" to TEXT_SMALLER,
        // Contraste
        "activa el alto contraste" to CONTRAST_ON,
        "ponme el fondo negro" to CONTRAST_ON,
        "quita el contraste alto" to CONTRAST_OFF,
        "vuelve al fondo blanco" to CONTRAST_OFF,
        // Ajustes
        "abre los ajustes" to OPEN_SETTINGS,
        "quiero ir a la configuración" to OPEN_SETTINGS,
        // Información
        "¿qué hora es?" to WHAT_TIME,
        "dime la hora" to WHAT_TIME,
        "¿qué día es hoy?" to WHAT_DATE,
        "¿cuál es la fecha de hoy?" to WHAT_DATE,
        "¿cómo te llamas?" to WHO_ARE_YOU,
        "¿quién eres tú?" to WHO_ARE_YOU,
        "¿qué puedes hacer?" to HELP,
        "no sé qué hacer" to HELP,
        "ayuda" to HELP,
        // Cortesía y respuestas
        "hola Lucía" to GREETING,
        "buenos días" to GREETING,
        "muchas gracias" to THANKS,
        "sí, claro" to YES,
        "no gracias" to NO,
        // Tareas futuras (se reconocen para responder con honestidad)
        "necesito una carta para pedir una cita médica" to CREATE_DOCUMENT,
        "quiero hacer una encuesta" to CREATE_SURVEY,
        "quiero llamar a mi hija" to CALL,
        "llama a mi hija María" to CALL,
        "mándale un mensaje a mi nieto" to SEND_MESSAGE,
        "quiero pedir un combo de pollo" to ORDER_FOOD,
        // Sin sentido para el catálogo
        "el perro de mi vecina ladra mucho" to UNKNOWN,
    )

    @Test
    fun entiendeElBancoDeFrases() {
        val failures = bank.mapNotNull { (phrase, expected) ->
            val got = understander.understand(phrase).intent
            if (got == expected) null else "«$phrase»: esperado $expected, obtenido $got"
        }
        val accuracy = 100 * (bank.size - failures.size) / bank.size
        println("Nivel 1: $accuracy % de ${bank.size} frases correctas")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun noConfundeUnNoConUnaOrden() {
        assertTrue(understander.understand("no repitas").intent != REPEAT)
        assertTrue(understander.understand("no pongas las letras más grandes").intent != TEXT_BIGGER)
    }

    @Test
    fun paraDentroDeUnaFraseNoSignificaDetente() {
        assertEquals(CREATE_DOCUMENT, understander.understand("una carta para mi hija").intent)
    }

    @Test
    fun toleraErroresDelReconocimientoDeVoz() {
        // "letraz grandez", "repiteme" pegado, etc.
        assertEquals(TEXT_BIGGER, understander.understand("letraz mas grandez").intent)
        assertEquals(REPEAT, understander.understand("repiteme").intent)
    }

    @Test
    fun extraeAQuienSeQuiereLlamar() {
        assertEquals("mi hija maria", understander.understand("Llama a mi hija María").slots["contacto"])
    }

    @Test
    fun ignoraElNombreDelAsistenteYLasPalabrasDeRelleno() {
        assertEquals(WHAT_TIME, understander.understand("Oye Lucía, por favor, ¿qué hora es?").intent)
    }

    @Test
    fun laDistanciaDeLevenshteinEsCorrecta() {
        assertEquals(3, TextMatching.levenshtein("kitten", "sitting"))
        assertEquals(0, TextMatching.levenshtein("hola", "hola"))
        assertEquals(4, TextMatching.levenshtein("", "hola"))
    }
}
