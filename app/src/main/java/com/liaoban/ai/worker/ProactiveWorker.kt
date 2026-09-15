package com.liaoban.ai.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.liaoban.ai.MainActivity
import com.liaoban.ai.ai.ChatEngine
import com.liaoban.ai.storage.AppDb
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ProactiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val hour = LocalDateTime.now().hour
        if (hour >= 23 || hour < 8) return Result.success()

        val db = AppDb.get(applicationContext)
        val engine = ChatEngine(applicationContext)
        val now = System.currentTimeMillis()

        for (contact in db.getContacts()) {
            if (!contact.proactiveEnabled) continue

            val last = db.getLastMessage(contact.id) ?: continue
            val sinceLastHours = (now - last.createdAt) / 3_600_000.0
            if (sinceLastHours < 8) continue

            val sinceProactiveHours = contact.lastProactiveAt?.let {
                (now - it) / 3_600_000.0
            } ?: 999.0

            if (sinceProactiveHours < 18) continue

            val dueSoon = db.getMemories(contact.id, 120).firstOrNull { memory ->
                memory.dueAt?.let {
                    abs(it - now) <= 18 * 3_600_000L
                } == true
            }

            val sixHourBucket = now / (6 * 3_600_000L)
            val sparseSocialWindow =
                sinceLastHours >= 24 &&
                    ((sixHourBucket + contact.id.toLong()) % 4L == 0L)

            if (dueSoon == null && !sparseSocialWindow) continue

            val reason = dueSoon?.let {
                "一条重要记忆临近时间：${it.content}"
            } ?: "已经超过24小时没有交流，可以判断是否有自然的共同话题重新开启对话"

            val text = engine.maybeGenerateProactive(contact, reason)
            if (!text.isNullOrBlank()) {
                showNotification(contact.id, contact.name, text)
            }
        }

        return Result.success()
    }

    private fun showNotification(contactId: Int, title: String, text: String) {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "ai_contact_messages"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "AI 联系人消息",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            applicationContext,
            contactId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        manager.notify(3000 + contactId, notification)
    }
}

object ProactiveScheduler {
    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
            1,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "liaoban_v2_proactive",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
