package com.example.notificationlog.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notificationlog.data.AppDatabase
import com.example.notificationlog.data.NotificationThread
import com.example.notificationlog.data.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationLogListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao by lazy { AppDatabase.get(this).notificationDao() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Preferences(this).isExcluded(sbn.packageName)) return
        scope.launch {
            runCatching {
                val p = NotificationMapper.parse(this@NotificationLogListenerService, sbn)
                val now = System.currentTimeMillis()
                val active = dao.findActive(p.packageName, p.key)
                if (active == null) {
                    val thread = NotificationThread(java.util.UUID.randomUUID().toString(), p.packageName, p.appName, p.key, now, now, null, true)
                    dao.insertThread(thread)
                    dao.insertVersion(NotificationMapper.toVersion(p, thread.id, now, "POSTED"))
                } else {
                    val latest = dao.latestVersion(active.id)
                    if (latest == null || latest.stateHash != p.hash || latest.eventType == "REMOVED") {
                        dao.insertVersion(NotificationMapper.toVersion(p, active.id, now, "UPDATED"))
                        dao.updateThread(active.id, now, true, null)
                    }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (Preferences(this).isExcluded(sbn.packageName)) return
        scope.launch {
            runCatching {
                val active = dao.findActive(sbn.packageName, sbn.key) ?: return@runCatching
                val latest = dao.latestVersion(active.id)
                val now = System.currentTimeMillis()
                if (latest?.eventType != "REMOVED") {
                    val p = runCatching { NotificationMapper.parse(this@NotificationLogListenerService, sbn) }.getOrNull()
                    if (p != null) {
                        dao.insertVersion(NotificationMapper.toVersion(p, active.id, now, "REMOVED"))
                    } else if (latest != null) {
                        dao.insertVersion(latest.copy(id = java.util.UUID.randomUUID().toString(), timestamp = now, eventType = "REMOVED"))
                    }
                }
                dao.updateThread(active.id, now, false, now)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
