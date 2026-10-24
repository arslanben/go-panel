package dev.opencodegometer.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.opencodegometer.data.ApiResult
import dev.opencodegometer.data.ErrorCode
import dev.opencodegometer.data.GoApi
import dev.opencodegometer.data.SecureStore
import dev.opencodegometer.data.SnapshotStore
import dev.opencodegometer.data.UsageSnapshot
import dev.opencodegometer.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val now = System.currentTimeMillis()
        val force = inputData.getBoolean(KEY_FORCE, false)

        if (!force) {
            val lastAttempt = SnapshotStore.load(context)?.attemptedAt
            if (lastAttempt != null && now - lastAttempt < MIN_INTERVAL_MS) {
                return Result.success()
            }
        }

        val key = SecureStore.getApiKey(context)
        if (key.isNullOrBlank()) {
            val previous = SnapshotStore.load(context)
            SnapshotStore.save(context, UsageSnapshot.failure(previous, ErrorCode.NO_KEY, now))
            WidgetUpdater.updateAll(context)
            return Result.success()
        }

        return when (val result = withContext(Dispatchers.IO) { GoApi.fetch(key) }) {
            is ApiResult.Success -> {
                SnapshotStore.save(
                    context,
                    UsageSnapshot.success(result.data, System.currentTimeMillis())
                )
                WidgetUpdater.updateAll(context)
                Result.success()
            }

            is ApiResult.Failure -> {
                val previous = SnapshotStore.load(context)
                SnapshotStore.save(
                    context,
                    UsageSnapshot.failure(previous, result.code, System.currentTimeMillis())
                )
                WidgetUpdater.updateAll(context)
                when (result.code) {
                    ErrorCode.UNAUTHORIZED, ErrorCode.NO_SUBSCRIPTION -> {
                        RefreshScheduler.cancel(context)
                        Result.success()
                    }
                    ErrorCode.NETWORK, ErrorCode.SERVER, ErrorCode.RATE_LIMITED -> Result.retry()
                    else -> Result.success()
                }
            }
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MIN_INTERVAL_MS = 8 * 60_000L
    }
}
