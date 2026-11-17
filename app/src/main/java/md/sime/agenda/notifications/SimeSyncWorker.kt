package md.sime.agenda.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import md.sime.agenda.data.SimeRepository
import java.util.concurrent.TimeUnit

class SimeSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repo = SimeRepository.getInstance(applicationContext)
        val newMessages = repo.checkForNewMessagesInBackground()
        newMessages.forEachIndexed { index, msg ->
            NotificationHelper.showTeacherMessageNotification(
                context = applicationContext,
                message = msg,
                notificationId = 2000 + (msg.id.hashCode() and 0x7FFF) + index
            )
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sime_messages_periodic_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SimeSyncWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
