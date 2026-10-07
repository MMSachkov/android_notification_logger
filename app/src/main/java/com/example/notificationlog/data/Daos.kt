package com.example.notificationlog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("""
        SELECT t.id, t.packageName, t.appName, t.notificationKey, t.firstSeenAt,
               t.lastChangedAt, t.lastRemovedAt, t.active,
               (SELECT v.eventType FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestEventType,
               (SELECT v.title FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTitle,
               (SELECT v.text FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestText,
               (SELECT v.timestamp FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTimestamp
        FROM notification_threads t
        ORDER BY t.lastChangedAt DESC
    """)
    fun observeThreadSummaries(): Flow<List<ThreadSummary>>

    @Query("""
        SELECT t.id, t.packageName, t.appName, t.notificationKey, t.firstSeenAt,
               t.lastChangedAt, t.lastRemovedAt, t.active,
               (SELECT v.eventType FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestEventType,
               (SELECT v.title FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTitle,
               (SELECT v.text FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestText,
               (SELECT v.timestamp FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTimestamp
        FROM notification_threads t
        WHERE t.appName LIKE '%' || :query || '%'
           OR t.packageName LIKE '%' || :query || '%'
           OR EXISTS (SELECT 1 FROM notification_versions v WHERE v.threadId = t.id AND (COALESCE(v.title, '') LIKE '%' || :query || '%' OR COALESCE(v.text, '') LIKE '%' || :query || '%'))
        ORDER BY t.lastChangedAt DESC
    """)
    fun observeThreadSummariesFiltered(query: String): Flow<List<ThreadSummary>>

    @Query("""
        SELECT t.id, t.packageName, t.appName, t.notificationKey, t.firstSeenAt,
               t.lastChangedAt, t.lastRemovedAt, t.active,
               (SELECT v.eventType FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestEventType,
               (SELECT v.title FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTitle,
               (SELECT v.text FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestText,
               (SELECT v.timestamp FROM notification_versions v WHERE v.threadId = t.id ORDER BY v.timestamp DESC LIMIT 1) AS latestTimestamp
        FROM notification_threads t
        WHERE (:eventType = 'ALL' OR EXISTS (SELECT 1 FROM notification_versions ev WHERE ev.threadId = t.id AND ev.eventType = :eventType))
          AND (:appQuery = '' OR lower(t.appName) LIKE '%' || lower(:appQuery) || '%' OR lower(t.packageName) LIKE '%' || lower(:appQuery) || '%')
          AND (:startMillis IS NULL OR EXISTS (
              SELECT 1 FROM notification_versions v
              WHERE v.threadId = t.id AND v.timestamp >= :startMillis
          ))
          AND (:endMillis IS NULL OR EXISTS (
              SELECT 1 FROM notification_versions v
              WHERE v.threadId = t.id AND v.timestamp < :endMillis
          ))
          AND (:startTimeMinute < 0 OR EXISTS (
              SELECT 1 FROM notification_versions v
              WHERE v.threadId = t.id
                AND (
                    (:crossesMidnight = 0 AND
                     ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                      CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) >= :startTimeMinute AND
                     ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                      CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) <= :endTimeMinute)
                    OR
                    (:crossesMidnight = 1 AND
                     (((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                       CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) >= :startTimeMinute OR
                      ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                       CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) <= :endTimeMinute)
                )
          ))
        ORDER BY t.lastChangedAt DESC
    """)
    fun observeThreadSummariesFiltered(
        eventType: String,
        appQuery: String,
        startMillis: Long?,
        endMillis: Long?,
        startTimeMinute: Int,
        endTimeMinute: Int,
        crossesMidnight: Int
    ): Flow<List<ThreadSummary>>

    @Query("""
        SELECT v.id, v.threadId, v.packageName, v.timestamp, v.eventType, v.title, v.text,
               v.subText, v.bigText, v.summary, v.category, v.channelId, v.channelName,
               v.groupKey, v.isGroupSummary, v.importance, v.priority, v.notificationNumber,
               v.flags, v.stateHash, v.extrasJson, t.appName
        FROM notification_versions v
        INNER JOIN notification_threads t ON t.id = v.threadId
        WHERE (:eventType = 'ALL' OR v.eventType = :eventType)
          AND (:appQuery = '' OR lower(t.appName) LIKE '%' || lower(:appQuery) || '%' OR lower(v.packageName) LIKE '%' || lower(:appQuery) || '%')
          AND (:startMillis IS NULL OR v.timestamp >= :startMillis)
          AND (:endMillis IS NULL OR v.timestamp < :endMillis)
          AND (
              :startTimeMinute < 0 OR
              (:crossesMidnight = 0 AND
               ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) >= :startTimeMinute AND
               ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) <= :endTimeMinute)
              OR
              (:crossesMidnight = 1 AND
               (((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                 CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) >= :startTimeMinute OR
                ((CAST(strftime('%H', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) * 60) +
                 CAST(strftime('%M', v.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER)) <= :endTimeMinute)
          )
        ORDER BY v.timestamp ASC
    """)
    suspend fun exportRows(
        eventType: String,
        appQuery: String,
        startMillis: Long?,
        endMillis: Long?,
        startTimeMinute: Int,
        endTimeMinute: Int,
        crossesMidnight: Int
    ): List<ExportRow>

    @Query("SELECT * FROM notification_versions WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun observeVersions(threadId: String): Flow<List<NotificationVersion>>

    @Query("SELECT * FROM notification_threads WHERE packageName = :packageName AND notificationKey = :notificationKey AND active = 1 LIMIT 1")
    suspend fun findActive(packageName: String, notificationKey: String): NotificationThread?

    @Query("SELECT * FROM notification_versions WHERE threadId = :threadId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestVersion(threadId: String): NotificationVersion?

    @Insert suspend fun insertThread(thread: NotificationThread)
    @Insert suspend fun insertVersion(version: NotificationVersion)

    @Query("UPDATE notification_threads SET lastChangedAt = :changedAt, active = :active, lastRemovedAt = :removedAt WHERE id = :threadId")
    suspend fun updateThread(threadId: String, changedAt: Long, active: Boolean, removedAt: Long?)

    @Query("DELETE FROM notification_versions WHERE timestamp < :cutoff")
    suspend fun deleteVersionsOlderThan(cutoff: Long): Int

    @Query("DELETE FROM notification_threads WHERE id NOT IN (SELECT DISTINCT threadId FROM notification_versions)")
    suspend fun deleteEmptyThreads(): Int

    @Query("SELECT COUNT(*) FROM notification_versions")
    fun observeVersionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_threads")
    fun observeThreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_versions WHERE eventType = :eventType")
    fun observeEventCount(eventType: String): Flow<Int>

    @Query("DELETE FROM notification_versions")
    suspend fun clearVersions()

    @Query("DELETE FROM notification_threads")
    suspend fun clearThreads()
}

data class ExportRow(
    val id: String,
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
    val extrasJson: String,
    val appName: String
)

data class ThreadSummary(
    val id: String,
    val packageName: String,
    val appName: String,
    val notificationKey: String,
    val firstSeenAt: Long,
    val lastChangedAt: Long,
    val lastRemovedAt: Long?,
    val active: Boolean,
    val latestEventType: String?,
    val latestTitle: String?,
    val latestText: String?,
    val latestTimestamp: Long?
)
