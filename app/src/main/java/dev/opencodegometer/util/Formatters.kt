package dev.opencodegometer.util

import android.content.Context
import dev.opencodegometer.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    fun percent(value: Int): String = "$value%"

    fun time(millis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))

    fun dateTime(millis: Long): String =
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))

    fun dateTime(instant: Instant): String =
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)

    fun resetIn(context: Context, instant: Instant): String {
        val seconds = Duration.between(Instant.now(), instant).seconds
        if (seconds <= 0) return context.getString(R.string.reset_now)

        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> context.getString(R.string.duration_days_hours, days, hours)
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            else -> context.getString(R.string.duration_minutes, minutes)
        }
    }

    fun resetLabel(context: Context, instant: Instant?): String =
        if (instant == null) {
            context.getString(R.string.reset_unknown)
        } else {
            context.getString(R.string.reset_in, resetIn(context, instant))
        }
}
