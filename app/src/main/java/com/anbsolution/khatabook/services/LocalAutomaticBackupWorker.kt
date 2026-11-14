package com.anbsolution.khatabook.services

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class LocalAutomaticBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cadence = AutomaticBackupCadence.fromStorageValue(
            inputData.getString(CADENCE_KEY)
        ) ?: return Result.failure()

        return try {
            LocalApiServer.start(applicationContext)
            val backup = LocalApiClient(applicationContext).createAutomaticBackup()
            LocalAutomaticBackupFiles.write(applicationContext, cadence, backup)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Automatic ${cadence.storageValue} backup failed.", e)
            Result.retry()
        }
    }

    companion object {
        const val CADENCE_KEY = "cadence"
        private const val TAG = "KhataAutoBackup"
    }
}

object LocalAutomaticBackupScheduler {
    fun createInitialBackup(context: Context) {
        val request = OneTimeWorkRequestBuilder<LocalAutomaticBackupWorker>()
            .setInputData(
                workDataOf(
                    LocalAutomaticBackupWorker.CADENCE_KEY to
                        AutomaticBackupCadence.DAILY.storageValue
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "khata-automatic-backup-initial",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        val workManager = WorkManager.getInstance(context.applicationContext)

        AutomaticBackupCadence.entries.forEach { cadence ->
            val request = PeriodicWorkRequestBuilder<LocalAutomaticBackupWorker>(
                cadence.intervalDays,
                TimeUnit.DAYS
            )
                .setInputData(workDataOf(LocalAutomaticBackupWorker.CADENCE_KEY to cadence.storageValue))
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "khata-automatic-backup-${cadence.storageValue}",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
