package dev.opencodegometer.widget

import android.appwidget.AppWidgetManager
import android.content.Context

class GoMeterWidgetProvider : BaseWidgetProvider() {
    override fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        ids: IntArray
    ) {
        WidgetUpdater.updateLarge(context, appWidgetManager, ids)
    }
}
