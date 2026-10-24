package dev.opencodegometer.util

import android.content.Context
import androidx.annotation.StringRes
import dev.opencodegometer.R
import dev.opencodegometer.data.ErrorCode

object ErrorMessages {
    @StringRes
    fun labelRes(code: ErrorCode): Int = when (code) {
        ErrorCode.NO_KEY -> R.string.error_no_key
        ErrorCode.UNAUTHORIZED -> R.string.error_unauthorized
        ErrorCode.NO_SUBSCRIPTION -> R.string.error_no_subscription
        ErrorCode.RATE_LIMITED -> R.string.error_rate_limited
        ErrorCode.NETWORK -> R.string.error_network
        ErrorCode.SERVER -> R.string.error_server
        ErrorCode.PARSE -> R.string.error_parse
    }

    fun label(context: Context, code: ErrorCode?): String? =
        code?.let { context.getString(labelRes(it)) }
}
