package app.screenlookup.dictionary

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.screenlookup.Di
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryUpdateJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = Di.dictionaryPreferences
            if (!prefs.autoUpdateEnabled().get()) return@withContext Result.success()

            val lastCheck = prefs.lastDictUpdateCheck().get()
            val intervalHours = prefs.autoUpdateInterval().get()
            val intervalMs = intervalHours * 60 * 60 * 1000L
            val now = System.currentTimeMillis()

            if (now - lastCheck >= intervalMs) {
                val updates = DictionaryUpdater.checkForUpdates(applicationContext, Di.httpClient)
                for (u in updates) {
                    DictionaryUpdater.installUpdate(applicationContext, u)
                }
                Di.reconcileDictionariesWithDisk()
                prefs.lastDictUpdateCheck().set(now)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "dict_auto_update"

        fun schedule(context: Context, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (hours <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<DictionaryUpdateJob>(
                hours.toLong(), TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
