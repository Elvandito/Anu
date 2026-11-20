package id.elvan.waktusolat.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import id.elvan.waktusolat.MainActivity
import id.elvan.waktusolat.R
import id.elvan.waktusolat.data.PrayerRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PrayerScheduler {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("prayer_times", "Pengingat waktu solat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pengingat saat masuk waktu solat. Suara dapat diubah melalui pengaturan Android."
            })
        }
    }

    fun scheduleNext(context: Context) {
        val times = PrayerRepository(context).cached() ?: return
        val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(java.util.Date(now))
        val candidates = times.entries().filter { it.first != "Terbit" }.mapNotNull { (name, time) ->
            runCatching { fmt.parse("$today $time")?.time }.getOrNull()?.let { name to it }
        }
        val next = candidates.filter { it.second > now }.minByOrNull { it.second } ?: run {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(tomorrow.time)
            times.entries().filter { it.first != "Terbit" }.mapNotNull { (name, time) ->
                runCatching { fmt.parse("$date $time")?.time }.getOrNull()?.let { name to it }
            }.minByOrNull { it.second }
        } ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).putExtra("prayer", next.first)
        val pending = PendingIntent.getBroadcast(context, 41, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, pending)
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("prayer") ?: "Waktu solat"
        if (name == "REFRESH") {
            runCatching { PrayerRepository(context).refresh() }
            PrayerScheduler.scheduleNext(context)
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("prayer_times", "Pengingat waktu solat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifikasi ketika masuk waktu solat"
            })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "prayer_times")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Waktu $name telah tiba")
            .setContentText("Saatnya menunaikan solat.")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build()
        manager.notify(name.hashCode(), notification)
        PrayerScheduler.scheduleNext(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { PrayerScheduler.scheduleNext(context) }
}
