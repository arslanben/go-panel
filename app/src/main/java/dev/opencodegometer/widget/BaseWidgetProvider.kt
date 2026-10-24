package dev.opencodegometer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.opencodegometer.MainActivity
import dev.opencodegometer.R
import dev.opencodegometer.data.SecureStore
import dev.opencodegometer.data.SnapshotStore
import dev.opencodegometer.work.RefreshScheduler

abstract class BaseWidgetProvider : AppWidgetProvider() {

    protected abstract fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        ids: IntArray
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        maybeRefresh(context)
    }

    override fun onEnabled(context: Context) {
        RefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val large = manager.getAppWidgetIds(
            ComponentName(context, GoMeterWidgetProvider::class.java)
        )
        val small = manager.getAppWidgetIds(
            ComponentName(context, GoMeterSmallWidgetProvider::class.java)
        )
        if (large.isEmpty() && small.isEmpty()) {
            RefreshScheduler.cancel(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            if (SecureStore.getApiKey(context).isNullOrBlank()) {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                Toast.makeText(context, R.string.refreshing, Toast.LENGTH_SHORT).show()
                RefreshScheduler.refreshNow(context, force = true)
            }
        }
    }

    private fun maybeRefresh(context: Context) {
        val snapshot = SnapshotStore.load(context)
        val fetchedAt = snapshot?.fetchedAt
        val stale = fetchedAt == null || System.currentTimeMillis() - fetchedAt > 15 * 60_000L
        if (!SecureStore.getApiKey(context).isNullOrBlank() && stale) {
            RefreshScheduler.refreshNow(context, force = false)
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.opencodegometer.action.REFRESH"
    }
}
