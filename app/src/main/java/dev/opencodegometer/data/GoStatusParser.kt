package dev.opencodegometer.data

import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

object GoStatusParser {
    fun parse(json: String): UsageData? {
        return try {
            val root = JSONObject(json)
            val access = root.optJSONObject("access") ?: return null
            val meters = access.optJSONObject("meters") ?: return null
            val fiveHour = parseMeter(meters.optJSONObject("fiveHour")) ?: return null
            val week = parseMeter(meters.optJSONObject("week")) ?: return null
            val month = parseMeter(meters.optJSONObject("month")) ?: return null

            UsageData(
                product = root.optString("product", "go"),
                fiveHour = fiveHour,
                week = week,
                month = month,
                renewsAt = parseInstant(access.optString("endsAt")),
                cancelAtPeriodEnd = root.optBoolean(
                    "cancelAtPeriodEnd",
                    access.optBoolean("cancelAtPeriodEnd", false)
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMeter(obj: JSONObject?): UsageMeter? {
        if (obj == null) return null
        val used = obj.optString("usedMicroCents").toLongOrNull() ?: return null
        val limit = obj.optString("limitMicroCents").toLongOrNull() ?: return null
        return UsageMeter(
            usedMicroCents = used,
            limitMicroCents = limit,
            startsAt = parseInstant(obj.optString("startsAt")),
            resetsAt = parseInstant(obj.optString("resetsAt"))
        )
    }

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
