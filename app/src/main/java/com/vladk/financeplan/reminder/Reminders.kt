package com.vladk.financeplan.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vladk.financeplan.MainActivity
import com.vladk.financeplan.R
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Reminder
import com.vladk.financeplan.data.Repository
import com.vladk.financeplan.ui.Fmt
import com.vladk.financeplan.ui.stringsFor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily "did you record today's expenses?" notification.
 *
 * One alarm is set for the next reminder time. When it fires, [ReminderReceiver] shows the
 * notification and sets the alarm for the following day. Alarms do not survive a restart of the
 * phone, so the receiver also sets it again after boot, an app update or a clock change.
 */
object Reminders {

    /** Put on the intent that opens the app from the notification or the launcher shortcut. */
    const val EXTRA_ADD_EXPENSE = "com.vladk.financeplan.ADD_EXPENSE"
    const val ACTION_REMIND = "com.vladk.financeplan.REMIND"

    private const val CHANNEL = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    /** The alarm may arrive up to this much after the set time; that lets Android save battery. */
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** The next moment the clock shows [hour]:[minute] – later today, or tomorrow. */
    fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Sets (or, when the reminder is off, removes) the alarm for the next reminder. Safe to call any time. */
    fun schedule(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val fire = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(fire)
        if (!reminder.enabled) return
        val at = nextTrigger(LocalDateTime.now(), reminder.hour, reminder.minute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // On the minute where Android allows exact alarms (it does for this app unless the user
        // switched "Alarms & reminders" off); otherwise within a few minutes after the set time.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, fire)
            }
        } catch (e: SecurityException) {
            // The exact-alarm access was taken away between the check and the call.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, fire)
        }
    }

    /** False when Android will not show the app's notifications (permission denied or switched off). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** What the notification says, given what is already recorded for [today]. Null = stay silent. */
    fun message(data: AppData, today: LocalDate): Pair<String, String>? {
        val s = stringsFor(data.settings.language)
        val recorded = data.expenses.filter { it.date == today.toString() }
        if (recorded.isEmpty()) return s.notifTitle to s.notifNothingYet
        if (data.settings.reminder.skipIfRecorded) return null
        val fmt = Fmt(s, data.settings.currency)
        return s.notifTitle to s.notifSoFar.format(fmt.money(recorded.sumOf { it.amount }), recorded.size)
    }

    fun show(context: Context, data: AppData) {
        if (!data.settings.reminder.enabled || !canNotify(context)) return
        val (title, text) = message(data, LocalDate.now()) ?: return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, stringsFor(data.settings.language).reminderChannel, NotificationManager.IMPORTANCE_DEFAULT),
        )
        // Tapping the notification opens the app directly on the new-expense form.
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ADD_EXPENSE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // canNotify() was checked above; the permission can still be revoked in between.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Nothing to do: the user took the permission away.
        }
    }
}

/** Shows the reminder when the alarm fires, and keeps the alarm set after boot, updates and clock changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = runCatching { Repository.readOnce(context) }.getOrNull() ?: return
        if (intent.action == Reminders.ACTION_REMIND) Reminders.show(context, data)
        Reminders.schedule(context, data.settings.reminder)
    }
}
