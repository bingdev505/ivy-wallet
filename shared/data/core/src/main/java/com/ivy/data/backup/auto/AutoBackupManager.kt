package com.ivy.data.backup.auto

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.backup.BackupDataUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val backupDataUseCase: BackupDataUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val backupDir = File(context.filesDir, "auto_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetFile = File(backupDir, "IvyWallet_AutoBackup_$timestamp.zip")

            backupDataUseCase.exportToBackupFile(targetFile)

            val backups = backupDir.listFiles { file -> file.extension == "zip" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            if (backups.size > 10) {
                backups.drop(10).forEach { it.delete() }
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Auto backup failed")
            Result.retry()
        }
    }
}

@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefs: SharedPrefs,
) {
    companion object {
        private const val UNIQUE_DAILY_BACKUP = "ivy_wallet_daily_auto_backup"
        private const val UNIQUE_INSTANT_BACKUP = "ivy_wallet_instant_auto_backup"
    }

    fun scheduleDailyBackup(enable: Boolean) {
        sharedPrefs.putBoolean(SharedPrefs.AUTO_BACKUP_DAILY, enable)
        if (enable) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_DAILY_BACKUP,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_DAILY_BACKUP)
        }
    }

    fun triggerInstantBackupIfEnabled() {
        if (!sharedPrefs.getBoolean(SharedPrefs.AUTO_BACKUP_INSTANT, false)) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_INSTANT_BACKUP,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun setInstantBackupEnabled(enable: Boolean) {
        sharedPrefs.putBoolean(SharedPrefs.AUTO_BACKUP_INSTANT, enable)
    }

    fun isDailyEnabled(): Boolean = sharedPrefs.getBoolean(SharedPrefs.AUTO_BACKUP_DAILY, false)
    fun isInstantEnabled(): Boolean = sharedPrefs.getBoolean(SharedPrefs.AUTO_BACKUP_INSTANT, false)
}
