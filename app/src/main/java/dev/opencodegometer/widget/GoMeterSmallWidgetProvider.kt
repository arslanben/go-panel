package dev.opencodegometer.widget

import android.appwidget.AppWidgetManager
import android.content.Context

class GoMeterSmallWidgetProvider : BaseWidgetProvider() {
    override fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        ids: IntArray
    ) {
        WidgetUpdater.updateSmall(context, appWidgetManager, ids)
    }
}
