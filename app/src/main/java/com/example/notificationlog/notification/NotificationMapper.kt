package com.example.notificationlog.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.notificationlog.data.NotificationVersion

object NotificationMapper {
    data class Parsed(
        val packageName: String,
        val key: String,
        val appName: String,
        val title: String?,
        val text: String?,
        val subText: String?,
        val bigText: String?,
        val summary: String?,
        val category: String?,
        val channelId: String?,
        val channelName: String?,
        val groupKey: String?,
        val isGroupSummary: Boolean,
        val importance: Int,
        val priority: Int,
        val number: Int,
        val flags: Int,
        val extrasJson: String,
        val hash: String
    )

    fun parse(context: Context, sbn: StatusBarNotification): Parsed {
        val n = sbn.notification
        val e = n.extras
        val extras = BundleSerializer.serialize(e)
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val summary = e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        val appName = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val channel = if (android.os.Build.VERSION.SDK_INT >= 26) runCatching {
            context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(n.channelId)
        }.getOrNull() else null
        val channelName = channel?.name?.toString()
        val importance = channel?.importance ?: 0
        val canonical = listOf(
            sbn.packageName, sbn.key, title, text, subText, bigText, summary, n.category,
            n.channelId, channelName, n.group, n.flags and Notification.FLAG_GROUP_SUMMARY != 0, n.priority, n.number, n.flags, extras
        ).joinToString("\u0000")
        return Parsed(sbn.packageName, sbn.key, appName, title, text, subText, bigText, summary, n.category,
            n.channelId, channelName, n.group, n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            importance, n.priority, n.number, n.flags, extras, BundleSerializer.sha256(canonical))
    }

    fun toVersion(p: Parsed, threadId: String, timestamp: Long, eventType: String): NotificationVersion = NotificationVersion(
        id = java.util.UUID.randomUUID().toString(), threadId = threadId, packageName = p.packageName,
        timestamp = timestamp, eventType = eventType, title = p.title, text = p.text, subText = p.subText,
        bigText = p.bigText, summary = p.summary, category = p.category, channelId = p.channelId,
        channelName = p.channelName, groupKey = p.groupKey, isGroupSummary = p.isGroupSummary,
        importance = p.importance, priority = p.priority, notificationNumber = p.number, flags = p.flags,
        stateHash = p.hash, extrasJson = p.extrasJson
    )
}
