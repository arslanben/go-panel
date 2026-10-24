package dev.opencodegometer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dev.opencodegometer.MainActivity
import dev.opencodegometer.R
import dev.opencodegometer.data.SecureStore
import dev.opencodegometer.data.SnapshotStore
import dev.opencodegometer.data.UsageData
import dev.opencodegometer.data.UsageMeter
import dev.opencodegometer.data.UsageSnapshot
import dev.opencodegometer.util.Formatters
import java.time.Duration
import java.time.Instant

object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val largeIds = manager.getAppWidgetIds(
            ComponentName(context, GoMeterWidgetProvider::class.java)
        )
        if (largeIds.isNotEmpty()) updateLarge(context, manager, largeIds)

        val smallIds = manager.getAppWidgetIds(
            ComponentName(context, GoMeterSmallWidgetProvider::class.java)
        )
        if (smallIds.isNotEmpty()) updateSmall(context, manager, smallIds)
    }

    fun updateLarge(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = SnapshotStore.load(context)
        val hasKey = !SecureStore.getApiKey(context).isNullOrBlank()
        ids.forEach { id ->
            manager.updateAppWidget(id, buildLargeViews(context, snapshot, hasKey))
        }
    }

    fun updateSmall(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = SnapshotStore.load(context)
        val hasKey = !SecureStore.getApiKey(context).isNullOrBlank()
        ids.forEach { id ->
            manager.updateAppWidget(id, buildSmallViews(context, snapshot, hasKey))
        }
    }

    private fun buildLargeViews(
        context: Context,
        snapshot: UsageSnapshot?,
        hasKey: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_go_meter)
        val data = snapshot?.data
        val titleRes = if (data?.isGoPlus == true) R.string.widget_title_plus else R.string.widget_title

        views.setTextViewText(R.id.widgetTitle, context.getString(titleRes))
        views.setViewVisibility(
            R.id.widgetDemoBadge,
            if (snapshot?.isDemo == true) View.VISIBLE else View.GONE
        )

        if (data != null) {
            bindLargeRow(
                context, views, R.id.rowFiveBadge, R.id.rowFiveReset,
                R.id.rowFiveBarGreen, R.id.rowFiveBarYellow, R.id.rowFiveBarRed, data.fiveHour
            )
            bindLargeRow(
                context, views, R.id.rowWeekBadge, R.id.rowWeekReset,
                R.id.rowWeekBarGreen, R.id.rowWeekBarYellow, R.id.rowWeekBarRed, data.week
            )
            bindLargeRow(
                context, views, R.id.rowMonthBadge, R.id.rowMonthReset,
                R.id.rowMonthBarGreen, R.id.rowMonthBarYellow, R.id.rowMonthBarRed, data.month
            )
            views.setTextViewText(R.id.widgetBigPercent, Formatters.percent(data.month.usedRounded))
            views.setTextColor(R.id.widgetBigPercent, usedColor(context, data.month.usedRounded))
        } else {
            bindEmptyLargeRow(
                context, views, R.id.rowFiveBadge, R.id.rowFiveReset,
                R.id.rowFiveBarGreen, R.id.rowFiveBarYellow, R.id.rowFiveBarRed
            )
            bindEmptyLargeRow(
                context, views, R.id.rowWeekBadge, R.id.rowWeekReset,
                R.id.rowWeekBarGreen, R.id.rowWeekBarYellow, R.id.rowWeekBarRed
            )
            bindEmptyLargeRow(
                context, views, R.id.rowMonthBadge, R.id.rowMonthReset,
                R.id.rowMonthBarGreen, R.id.rowMonthBarYellow, R.id.rowMonthBarRed
            )
            views.setTextViewText(R.id.widgetBigPercent, context.getString(R.string.reset_unknown))
            views.setTextColor(
                R.id.widgetBigPercent,
                ContextCompat.getColor(context, R.color.widget_text_secondary)
            )
        }

        val error = snapshot?.error
        val fetchedAt = snapshot?.fetchedAt
        val statusText: String
        val statusColor: Int
        when {
            !hasKey -> {
                statusText = context.getString(R.string.widget_setup_short)
                statusColor = ContextCompat.getColor(context, R.color.widget_text_secondary)
            }
            error != null -> {
                statusText = context.getString(R.string.widget_error_short)
                statusColor = ContextCompat.getColor(context, R.color.danger)
            }
            fetchedAt != null -> {
                statusText = Formatters.time(fetchedAt)
                statusColor = ContextCompat.getColor(context, R.color.widget_text_secondary)
            }
            else -> {
                statusText = context.getString(R.string.widget_refresh_short)
                statusColor = ContextCompat.getColor(context, R.color.widget_text_secondary)
            }
        }
        views.setTextViewText(R.id.widgetStatus, statusText)
        views.setTextColor(R.id.widgetStatus, statusColor)
        bindEnds(context, views, R.id.widgetEnds, data)
        bindClicks(
            context, views, R.id.widgetRoot, R.id.widgetSettings,
            GoMeterWidgetProvider::class.java, hasKey
        )
        return views
    }

    private fun buildSmallViews(
        context: Context,
        snapshot: UsageSnapshot?,
        hasKey: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_go_meter_small)
        val data = snapshot?.data
        val titleRes = if (data?.isGoPlus == true) R.string.widget_title_plus else R.string.widget_title

        views.setTextViewText(R.id.smallWidgetTitle, context.getString(titleRes))
        bindEnds(context, views, R.id.smallWidgetEnds, data)

        if (data != null) {
            views.setTextViewText(
                R.id.smallWidgetBigPercent,
                Formatters.percent(data.month.usedRounded)
            )
            views.setTextColor(
                R.id.smallWidgetBigPercent,
                usedColor(context, data.month.usedRounded)
            )
            bindSmallRow(
                context, views, R.id.smallRowFivePercent,
                R.id.smallRowFiveBarGreen, R.id.smallRowFiveBarYellow, R.id.smallRowFiveBarRed,
                data.fiveHour
            )
            bindSmallRow(
                context, views, R.id.smallRowWeekPercent,
                R.id.smallRowWeekBarGreen, R.id.smallRowWeekBarYellow, R.id.smallRowWeekBarRed,
                data.week
            )
            bindSmallRow(
                context, views, R.id.smallRowMonthPercent,
                R.id.smallRowMonthBarGreen, R.id.smallRowMonthBarYellow, R.id.smallRowMonthBarRed,
                data.month
            )
        } else {
            views.setTextViewText(R.id.smallWidgetBigPercent, context.getString(R.string.reset_unknown))
            views.setTextColor(
                R.id.smallWidgetBigPercent,
                ContextCompat.getColor(context, R.color.widget_text_secondary)
            )
            bindEmptySmallRow(
                context, views, R.id.smallRowFivePercent,
                R.id.smallRowFiveBarGreen, R.id.smallRowFiveBarYellow, R.id.smallRowFiveBarRed
            )
            bindEmptySmallRow(
                context, views, R.id.smallRowWeekPercent,
                R.id.smallRowWeekBarGreen, R.id.smallRowWeekBarYellow, R.id.smallRowWeekBarRed
            )
            bindEmptySmallRow(
                context, views, R.id.smallRowMonthPercent,
                R.id.smallRowMonthBarGreen, R.id.smallRowMonthBarYellow, R.id.smallRowMonthBarRed
            )
        }

        bindClicks(
            context, views, R.id.smallWidgetRoot, R.id.smallWidgetSettings,
            GoMeterSmallWidgetProvider::class.java, hasKey
        )
        return views
    }

    private fun bindLargeRow(
        context: Context,
        views: RemoteViews,
        badgeId: Int,
        resetId: Int,
        greenId: Int,
        yellowId: Int,
        redId: Int,
        meter: UsageMeter
    ) {
        val used = meter.usedRounded
        views.setTextViewText(badgeId, context.getString(R.string.used_badge, used))
        views.setInt(badgeId, "setBackgroundResource", badgeBackground(used))
        views.setTextColor(badgeId, usedColor(context, used))
        views.setTextViewText(resetId, Formatters.resetLabel(context, meter.resetsAt))
        bindBar(views, greenId, yellowId, redId, used)
    }

    private fun bindEmptyLargeRow(
        context: Context,
        views: RemoteViews,
        badgeId: Int,
        resetId: Int,
        greenId: Int,
        yellowId: Int,
        redId: Int
    ) {
        views.setTextViewText(badgeId, context.getString(R.string.reset_unknown))
        views.setInt(badgeId, "setBackgroundResource", R.drawable.badge_used_background)
        views.setTextColor(
            badgeId,
            ContextCompat.getColor(context, R.color.widget_text_secondary)
        )
        views.setTextViewText(resetId, context.getString(R.string.reset_unknown))
        views.setViewVisibility(greenId, View.VISIBLE)
        views.setViewVisibility(yellowId, View.GONE)
        views.setViewVisibility(redId, View.GONE)
        views.setProgressBar(greenId, 100, 0, false)
    }

    private fun bindSmallRow(
        context: Context,
        views: RemoteViews,
        percentId: Int,
        greenId: Int,
        yellowId: Int,
        redId: Int,
        meter: UsageMeter
    ) {
        val used = meter.usedRounded
        views.setTextViewText(percentId, Formatters.percent(used))
        views.setTextColor(percentId, usedColor(context, used))
        bindBar(views, greenId, yellowId, redId, used)
    }

    private fun bindEmptySmallRow(
        context: Context,
        views: RemoteViews,
        percentId: Int,
        greenId: Int,
        yellowId: Int,
        redId: Int
    ) {
        views.setTextViewText(percentId, context.getString(R.string.reset_unknown))
        views.setTextColor(percentId, ContextCompat.getColor(context, R.color.widget_text_secondary))
        views.setViewVisibility(greenId, View.VISIBLE)
        views.setViewVisibility(yellowId, View.GONE)
        views.setViewVisibility(redId, View.GONE)
        views.setProgressBar(greenId, 100, 0, false)
    }

    private fun bindBar(
        views: RemoteViews,
        greenId: Int,
        yellowId: Int,
        redId: Int,
        used: Int
    ) {
        val band = usedBand(used)
        views.setViewVisibility(greenId, if (band == UsedBand.GREEN) View.VISIBLE else View.GONE)
        views.setViewVisibility(yellowId, if (band == UsedBand.YELLOW) View.VISIBLE else View.GONE)
        views.setViewVisibility(redId, if (band == UsedBand.RED) View.VISIBLE else View.GONE)
        val activeId = when (band) {
            UsedBand.GREEN -> greenId
            UsedBand.YELLOW -> yellowId
            UsedBand.RED -> redId
        }
        views.setProgressBar(activeId, 100, used, false)
    }

    private fun bindEnds(context: Context, views: RemoteViews, viewId: Int, data: UsageData?) {
        val days = data?.renewsAt?.let {
            Duration.between(Instant.now(), it).toDays().coerceAtLeast(0)
        }
        if (days == null) {
            views.setViewVisibility(viewId, View.GONE)
        } else {
            views.setTextViewText(
                viewId,
                context.getString(R.string.subscription_days_short, days)
            )
            views.setViewVisibility(viewId, View.VISIBLE)
        }
    }

    private fun bindClicks(
        context: Context,
        views: RemoteViews,
        rootId: Int,
        settingsId: Int,
        providerClass: Class<out AppWidgetProvider>,
        hasKey: Boolean
    ) {
        val refreshPending = PendingIntent.getBroadcast(
            context,
            100,
            Intent(context, providerClass).setAction(BaseWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val configPending = PendingIntent.getActivity(
            context,
            101,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(settingsId, configPending)
        views.setOnClickPendingIntent(rootId, if (hasKey) refreshPending else configPending)
    }

    private fun badgeBackground(used: Int): Int = when (usedBand(used)) {
        UsedBand.GREEN -> R.drawable.badge_used_background
        UsedBand.YELLOW -> R.drawable.badge_warning_background
        UsedBand.RED -> R.drawable.badge_danger_background
    }

    private fun usedColor(context: Context, used: Int): Int = when (usedBand(used)) {
        UsedBand.GREEN -> ContextCompat.getColor(context, R.color.accent)
        UsedBand.YELLOW -> ContextCompat.getColor(context, R.color.warning)
        UsedBand.RED -> ContextCompat.getColor(context, R.color.danger)
    }

    private fun usedBand(used: Int): UsedBand = when {
        used < 50 -> UsedBand.GREEN
        used < 75 -> UsedBand.YELLOW
        else -> UsedBand.RED
    }

    private enum class UsedBand { GREEN, YELLOW, RED }
}
