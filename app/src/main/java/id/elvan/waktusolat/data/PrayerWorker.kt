package id.elvan.waktusolat.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.elvan.waktusolat.notify.PrayerScheduler

class PrayerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        PrayerRepository(applicationContext).refresh()
        PrayerScheduler.scheduleNext(applicationContext)
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
