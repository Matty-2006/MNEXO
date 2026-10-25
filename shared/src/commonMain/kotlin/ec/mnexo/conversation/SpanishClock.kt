package ec.mnexo.conversation

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Da la fecha y hora local. Es una interfaz para poder usar una hora fija en las pruebas. */
fun interface TimeProvider {
    fun now(): LocalDateTime
}

@OptIn(ExperimentalTime::class)
val SystemTime = TimeProvider { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

@OptIn(ExperimentalTime::class)
fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Dice la hora y la fecha como las diría una persona en Ecuador. */
object SpanishClock {
    private val weekdays = mapOf(
        DayOfWeek.MONDAY to "lunes", DayOfWeek.TUESDAY to "martes", DayOfWeek.WEDNESDAY to "miércoles",
        DayOfWeek.THURSDAY to "jueves", DayOfWeek.FRIDAY to "viernes", DayOfWeek.SATURDAY to "sábado",
        DayOfWeek.SUNDAY to "domingo",
    )
    private val months = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** 15:25 → "Son las 3 y 25 de la tarde."; 13:00 → "Es la 1 en punto de la tarde." */
    fun time(now: LocalDateTime): String {
        val hour24 = now.hour
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        val minutes = when (now.minute) {
            0 -> "en punto"
            15 -> "y cuarto"
            30 -> "y media"
            else -> "y ${now.minute}"
        }
        val period = when (hour24) {
            in 0..5 -> "de la madrugada"
            in 6..11 -> "de la mañana"
            in 12..18 -> "de la tarde"
            else -> "de la noche"
        }
        val verb = if (hour12 == 1) "Es la" else "Son las"
        return "$verb $hour12 $minutes $period."
    }

    /** → "Hoy es sábado 24 de octubre de 2026." */
    fun date(now: LocalDateTime): String {
        val weekday = weekdays.getValue(now.dayOfWeek)
        val month = months[now.month.ordinal]
        return "Hoy es $weekday ${now.day} de $month de ${now.year}."
    }
}
