package dev.opencodegometer.data

import java.time.Instant
import kotlin.math.roundToInt

enum class ErrorCode {
    NO_KEY,
    UNAUTHORIZED,
    NO_SUBSCRIPTION,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    PARSE
}

enum class MeterKind {
    FIVE_HOUR,
    WEEK,
    MONTH
}

data class UsageMeter(
    val usedMicroCents: Long,
    val limitMicroCents: Long,
    val startsAt: Instant?,
    val resetsAt: Instant?
) {
    val usedPercent: Double
        get() = if (limitMicroCents <= 0L) {
            0.0
        } else {
            (usedMicroCents.toDouble() / limitMicroCents.toDouble() * 100.0).coerceIn(0.0, 100.0)
        }

    val remainingPercent: Double
        get() = if (limitMicroCents <= 0L) 100.0 else (100.0 - usedPercent).coerceIn(0.0, 100.0)

    val usedRounded: Int
        get() = usedPercent.roundToInt().coerceIn(0, 100)

    val remainingRounded: Int
        get() = remainingPercent.roundToInt().coerceIn(0, 100)
}

data class UsageData(
    val product: String,
    val fiveHour: UsageMeter,
    val week: UsageMeter,
    val month: UsageMeter,
    val renewsAt: Instant?,
    val cancelAtPeriodEnd: Boolean
) {
    fun all(): List<Pair<MeterKind, UsageMeter>> = listOf(
        MeterKind.FIVE_HOUR to fiveHour,
        MeterKind.WEEK to week,
        MeterKind.MONTH to month
    )

    fun mostUsed(): Pair<MeterKind, UsageMeter> =
        listOf(
            MeterKind.MONTH to month,
            MeterKind.WEEK to week,
            MeterKind.FIVE_HOUR to fiveHour
        ).maxByOrNull { it.second.usedPercent }!!

    val isGoPlus: Boolean
        get() = product.equals("go-plus", ignoreCase = true)
}

data class UsageSnapshot(
    val data: UsageData?,
    val fetchedAt: Long?,
    val attemptedAt: Long?,
    val error: ErrorCode?,
    val isDemo: Boolean = false
) {
    val hasData: Boolean
        get() = data != null

    companion object {
        fun success(data: UsageData, now: Long): UsageSnapshot =
            UsageSnapshot(data = data, fetchedAt = now, attemptedAt = now, error = null)

        fun failure(previous: UsageSnapshot?, error: ErrorCode, now: Long): UsageSnapshot =
            UsageSnapshot(
                data = previous?.data,
                fetchedAt = previous?.fetchedAt,
                attemptedAt = now,
                error = error,
                isDemo = previous?.isDemo ?: false
            )

        fun demo(now: Long): UsageSnapshot {
            val current = Instant.now()
            val data = UsageData(
                product = "go",
                fiveHour = UsageMeter(
                    usedMicroCents = 12_000_000L,
                    limitMicroCents = 1_200_000_000L,
                    startsAt = current,
                    resetsAt = current.plusSeconds(3 * 3600 + 56 * 60)
                ),
                week = UsageMeter(
                    usedMicroCents = 1_200_000_000L,
                    limitMicroCents = 3_000_000_000L,
                    startsAt = current,
                    resetsAt = current.plusSeconds(86400 + 2 * 3600)
                ),
                month = UsageMeter(
                    usedMicroCents = 1_920_000_000L,
                    limitMicroCents = 6_000_000_000L,
                    startsAt = current,
                    resetsAt = current.plusSeconds(13 * 86400 + 12 * 3600)
                ),
                renewsAt = current.plusSeconds(13 * 86400 + 12 * 3600),
                cancelAtPeriodEnd = false
            )
            return UsageSnapshot(data = data, fetchedAt = now, attemptedAt = now, error = null, isDemo = true)
        }
    }
}

sealed class ApiResult {
    data class Success(val data: UsageData) : ApiResult()
    data class Failure(val code: ErrorCode, val httpStatus: Int? = null) : ApiResult()
}
