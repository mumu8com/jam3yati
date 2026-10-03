package ly.jam3yati.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class PaymentReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = SupabaseRepository(applicationContext)
            if (!repo.isSignedIn()) return Result.success()
            val today = LocalDate.now()
            repo.associations().forEach { association ->
                repo.installments(association.id).filter { it.status != "paid" }.filter {
                    val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull() ?: return@filter false
                    !due.isBefore(today) && !due.isAfter(today.plusDays(1))
                }.take(3).forEach { item ->
                    val body = item.memberName.ifBlank { "عضو" } + " - " + association.name + ": " + (item.amount - item.paidAmount) + " دينار، الاستحقاق " + item.dueDate
                    notify("موعد دفعة قريب", body)
                }
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
    private fun notify(title: String, body: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "payment_due"
        manager.createNotificationChannel(NotificationChannel(channelId, "تنبيهات الدفعات", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}