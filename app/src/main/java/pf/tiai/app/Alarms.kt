package pf.tiai.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Programme le rappel sonore et l'heure limite du signe de vie, chaque jour. */
object Alarms {
    const val ACTION_REMINDER = "pf.tiai.app.REMINDER"
    const val ACTION_DEADLINE = "pf.tiai.app.DEADLINE"
    private const val CHANNEL = "signe_de_vie"

    fun scheduleAll(ctx: Context) {
        val store = Store(ctx)
        schedule(ctx, ACTION_REMINDER, store.reminderAt, 1)
        schedule(ctx, ACTION_DEADLINE, store.deadline, 2)
    }

    private fun schedule(ctx: Context, action: String, minutes: Int, code: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        val intent = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(
            ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Alarme non exacte : Android peut la retarder de quelques minutes quand le téléphone dort.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun notify(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Signe de vie", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            // notifications refusées par l'utilisateur
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = Store(ctx)
        val waiting = !store.isPaused() && !store.checkedInToday()
        when (intent.action) {
            Alarms.ACTION_REMINDER -> if (waiting) {
                Alarms.notify(
                    ctx, 1, "Tout va bien ?",
                    "Ouvrez Tīaʻi et appuyez sur « Je vais bien » avant " + Store.hm(store.deadline) + "."
                )
            }
            Alarms.ACTION_DEADLINE -> {
                val today = Store.dayKey(System.currentTimeMillis())
                if (waiting && store.alertSentDay != today) {
                    store.alertSentDay = today
                    val n = SmsSender.sendToAll(ctx, Messages.noSign(store))
                    Alarms.notify(
                        ctx, 2, "Pas de signe de vie aujourd'hui",
                        if (n > 0) "Vos proches ont été prévenus par SMS. Ouvrez Tīaʻi pour les rassurer."
                        else "Aucun SMS n'a pu partir. Ouvrez Tīaʻi."
                    )
                }
            }
        }
        // Après chaque alarme, après un redémarrage ou une mise à jour : on reprogramme.
        Alarms.scheduleAll(ctx)
    }
}
