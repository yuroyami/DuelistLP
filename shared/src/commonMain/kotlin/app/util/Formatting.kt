package app.util

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatDateTime(epochMillis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    @Suppress("DEPRECATION")
    val date = "${ldt.year}-${ldt.monthNumber.pad()}-${ldt.day.pad()}"
    val time = "${ldt.hour.pad()}:${ldt.minute.pad()}"
    return "$date  $time"
}

fun formatClockTime(epochMillis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.hour.pad()}:${ldt.minute.pad()}:${ldt.second.pad()}"
}

fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

private fun Int.pad(): String = if (this < 10) "0$this" else toString()
