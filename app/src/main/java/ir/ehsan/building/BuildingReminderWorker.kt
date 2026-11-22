package ir.ehsan.building

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class BuildingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.schedule(this)
    }
}

object ReminderScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BuildingReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("building_monthly_reminders", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class BuildingReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("building_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reminders", true)) return Result.success()
        val cal = android.icu.util.Calendar.getInstance(android.icu.util.ULocale("fa_IR@calendar=persian"))
        val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
        val monthKey = "${cal.get(android.icu.util.Calendar.YEAR)}-${cal.get(android.icu.util.Calendar.MONTH) + 1}"
        val chargeDay = (prefs.getString("reminder_day", "1") ?: "1").toIntOrNull() ?: 1
        val elevatorDay = (prefs.getString("elevator_reminder_day", "1") ?: "1").toIntOrNull() ?: 1
        createChannel()
        if (day == chargeDay && prefs.getString("charge_notified_month", "") != monthKey) {
            notify(1001, "یادآوری شارژ ماهانه ساختمان", "زمان پرداخت شارژ ماهانه ساختمان فرا رسیده است.")
            prefs.edit().putString("charge_notified_month", monthKey).apply()
        }
        if (day == elevatorDay && prefs.getString("elevator_notified_month", "") != monthKey) {
            notify(1002, "یادآوری سرویس آسانسور", "زمان سرویس ماهانه آسانسور ساختمان فرا رسیده است.")
            prefs.edit().putString("elevator_notified_month", monthKey).apply()
        }
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("building_reminders", "یادآوری‌های ساختمان", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun notify(id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(applicationContext, "building_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
