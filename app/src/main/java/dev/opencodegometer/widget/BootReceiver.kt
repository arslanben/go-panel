package dev.opencodegometer.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.opencodegometer.data.SecureStore
import dev.opencodegometer.work.RefreshScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val largeIds = manager.getAppWidgetIds(
                ComponentName(context, GoMeterWidgetProvider::class.java)
            )
            val smallIds = manager.getAppWidgetIds(
                ComponentName(context, GoMeterSmallWidgetProvider::class.java)
            )
            if ((largeIds.isNotEmpty() || smallIds.isNotEmpty()) &&
                !SecureStore.getApiKey(context).isNullOrBlank()
            ) {
                RefreshScheduler.schedule(context)
                RefreshScheduler.refreshNow(context, force = true)
            }
            WidgetUpdater.updateAll(context)
        }
    }
}
