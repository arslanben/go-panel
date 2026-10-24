package dev.opencodegometer.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.opencodegometer.data.SecureStore

class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RefreshScheduler.ACTION_ALARM &&
            !SecureStore.getApiKey(context).isNullOrBlank()
        ) {
            RefreshScheduler.refreshNow(context, force = false)
        }
    }
}
