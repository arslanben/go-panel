package dev.opencodegometer.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.opencodegometer.data.SecureStore
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val PERIODIC_WORK = "go-meter-periodic"
    private const val FORCE_WORK = "go-meter-refresh-force"
    private const val BACKGROUND_WORK = "go-meter-refresh-background"
    private const val ALARM_REQUEST_CODE = 200
    private const val ALARM_INTERVAL_MS = 10 * 60 * 1000L

    const val ACTION_ALARM = "dev.opencodegometer.action.ALARM_REFRESH"

    fun schedule(context: Context) {
        if (SecureStore.getApiKey(context).isNullOrBlank()) {
            cancel(context)
            return
        }

        val periodicRequest = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, RefreshAlarmReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
            ALARM_INTERVAL_MS,
            alarmIntent
        )
    }

    fun refreshNow(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(RefreshWorker.KEY_FORCE to force))
            .build()

        if (force) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FORCE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } else {
            WorkManager.getInstance(context).enqueueUniqueWork(
                BACKGROUND_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(FORCE_WORK)
        workManager.cancelUniqueWork(BACKGROUND_WORK)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, RefreshAlarmReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmIntent != null) {
            alarmManager.cancel(alarmIntent)
            alarmIntent.cancel()
        }
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
