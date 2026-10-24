package dev.opencodegometer.data

import android.content.Context
import org.json.JSONObject
import java.time.Instant

object SnapshotStore {
    private const val PREFS = "usage_snapshot"
    private const val KEY = "snapshot"

    fun load(context: Context): UsageSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, snapshot: UsageSnapshot) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, toJson(snapshot).toString())
                .apply()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun toJson(snapshot: UsageSnapshot): JSONObject {
        val root = JSONObject()
        root.put("fetchedAt", snapshot.fetchedAt ?: JSONObject.NULL)
        root.put("attemptedAt", snapshot.attemptedAt ?: JSONObject.NULL)
        root.put("error", snapshot.error?.name ?: JSONObject.NULL)
        root.put("isDemo", snapshot.isDemo)
        snapshot.data?.let { data ->
            val json = JSONObject()
            json.put("product", data.product)
            json.put("renewsAt", data.renewsAt?.toEpochMilli() ?: JSONObject.NULL)
            json.put("cancelAtPeriodEnd", data.cancelAtPeriodEnd)
            json.put("fiveHour", meterToJson(data.fiveHour))
            json.put("week", meterToJson(data.week))
            json.put("month", meterToJson(data.month))
            root.put("data", json)
        }
        return root
    }

    private fun meterToJson(meter: UsageMeter): JSONObject = JSONObject().apply {
        put("used", meter.usedMicroCents)
        put("limit", meter.limitMicroCents)
        put("startsAt", meter.startsAt?.toEpochMilli() ?: JSONObject.NULL)
        put("resetsAt", meter.resetsAt?.toEpochMilli() ?: JSONObject.NULL)
    }

    private fun fromJson(root: JSONObject): UsageSnapshot {
        val data = root.optJSONObject("data")?.let { json ->
            UsageData(
                product = json.optString("product", "go"),
                fiveHour = meterFromJson(json.getJSONObject("fiveHour")),
                week = meterFromJson(json.getJSONObject("week")),
                month = meterFromJson(json.getJSONObject("month")),
                renewsAt = json.optLongOrNull("renewsAt")?.let { Instant.ofEpochMilli(it) },
                cancelAtPeriodEnd = json.optBoolean("cancelAtPeriodEnd", false)
            )
        }
        return UsageSnapshot(
            data = data,
            fetchedAt = root.optLongOrNull("fetchedAt"),
            attemptedAt = root.optLongOrNull("attemptedAt"),
            error = if (root.isNull("error")) {
                null
            } else {
                root.optString("error")
                    .takeIf { it.isNotBlank() }
                    ?.let { name -> ErrorCode.entries.firstOrNull { it.name == name } }
            },
            isDemo = root.optBoolean("isDemo", false)
        )
    }

    private fun meterFromJson(json: JSONObject): UsageMeter = UsageMeter(
        usedMicroCents = json.optLong("used", 0L),
        limitMicroCents = json.optLong("limit", 0L),
        startsAt = json.optLongOrNull("startsAt")?.let { Instant.ofEpochMilli(it) },
        resetsAt = json.optLongOrNull("resetsAt")?.let { Instant.ofEpochMilli(it) }
    )

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name, 0L)
}
