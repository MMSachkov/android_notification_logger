package com.example.notificationlog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notification_threads", indices = [Index(value = ["packageName", "notificationKey", "active"])])
data class NotificationThread(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val notificationKey: String,
    val firstSeenAt: Long,
    val lastChangedAt: Long,
    val lastRemovedAt: Long?,
    val active: Boolean
)

@Entity(tableName = "notification_versions", indices = [Index("threadId"), Index("timestamp"), Index("packageName"), Index("eventType")])
data class NotificationVersion(
    @PrimaryKey val id: String,
    val threadId: String,
    val packageName: String,
    val timestamp: Long,
    val eventType: String,
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
    val notificationNumber: Int,
    val flags: Int,
    val stateHash: String,
    val extrasJson: String
)
