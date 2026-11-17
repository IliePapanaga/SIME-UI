package md.sime.agenda.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import md.sime.agenda.MainActivity
import md.sime.agenda.R
import md.sime.agenda.model.TeacherMessage

object NotificationHelper {

    const val CHANNEL_ID = "sime_messages"
    const val EXTRA_OPEN_TAB = "open_tab"
    const val TAB_MESEJE = "mesaje"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesaje profesori",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificări când primești un mesaj nou în Agenda SIME"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showTeacherMessageNotification(context: Context, message: TeacherMessage, notificationId: Int) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TAB, TAB_MESEJE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = buildString {
            append(message.professor.ifBlank { "Mesaj nou" })
            if (message.subject.isNotBlank()) {
                append(" • ")
                append(message.subject)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showTestNotification(context: Context) {
        showTeacherMessageNotification(
            context = context,
            message = TeacherMessage(
                id = "test_notif",
                date = "Acum",
                professor = "Agenda",
                subject = "Notificări active",
                text = "Vei primi notificări automate când un profesor trimite un mesaj nou."
            ),
            notificationId = 9001
        )
    }
}
