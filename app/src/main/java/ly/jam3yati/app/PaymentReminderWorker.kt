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
                val items = repo.installments(association.id).filter { it.status != "paid" }
                items.filter {
                    val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull() ?: return@filter false
                    due.isAfter(today) && !due.isAfter(today.plusDays(3))
                }.take(5).forEach { item ->
                    val due = LocalDate.parse(item.dueDate)
                    notify(
                        "استحقاق قادم",
                        association.name + " • " + item.memberName + ": " +
                            moneyText(item.amount - item.paidAmount) + " د.ل، الموعد " + item.dueDate,
                        item.id.hashCode()
                    )
                }

                items.filter {
                    val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull() ?: return@filter false
                    due.isEqual(today)
                }.take(5).forEach { item ->
                    notify(
                        "موعد الدفع اليوم",
                        association.name + " • " + item.memberName + ": المتبقي " +
                            moneyText(item.amount - item.paidAmount) + " د.ل",
                        (item.id.hashCode() * 31) + 1
                    )
                }

                items.filter {
                    val due = runCatching { LocalDate.parse(it.dueDate) }.getOrNull() ?: return@filter false
                    due.isBefore(today)
                }.take(5).forEach { item ->
                    notify(
                        "دفعة متأخرة",
                        association.name + " • " + item.memberName + ": متأخر عن موعد " +
                            item.dueDate + "، المتبقي " + moneyText(item.amount - item.paidAmount) + " د.ل",
                        (item.id.hashCode() * 31) + 2
                    )
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun moneyText(value: Double): String =
        String.format(java.util.Locale.US, "%.2f", value.coerceAtLeast(0.0))

    private fun notify(title: String, body: String, id: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "payment_due"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "تنبيهات الدفعات", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(kotlin.math.abs(id), notification)
    }
}
