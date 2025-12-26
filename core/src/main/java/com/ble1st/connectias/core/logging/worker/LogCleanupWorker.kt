package com.ble1st.connectias.core.logging.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ble1st.connectias.core.database.dao.SystemLogDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class LogCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val logDao: SystemLogDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.i("Starting log cleanup...")
            // Keep logs for 7 days
            val retentionDays = 7L
            val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)
            
            logDao.deleteOldLogs(threshold)
            
            Timber.i("Log cleanup completed. Deleted logs older than $retentionDays days.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup logs")
            Result.retry()
        }
    }
}
