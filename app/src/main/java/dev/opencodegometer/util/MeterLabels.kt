package dev.opencodegometer.util

import androidx.annotation.StringRes
import dev.opencodegometer.R
import dev.opencodegometer.data.MeterKind

@StringRes
fun MeterKind.labelRes(): Int = when (this) {
    MeterKind.FIVE_HOUR -> R.string.meter_five_hour
    MeterKind.WEEK -> R.string.meter_week
    MeterKind.MONTH -> R.string.meter_month
}
