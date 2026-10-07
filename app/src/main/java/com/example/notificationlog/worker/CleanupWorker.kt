package com.example.notificationlog.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notificationlog.data.AppDatabase
import com.example.notificationlog.data.Preferences

class CleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val days = Preferences(applicationContext).retentionDays
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val dao = AppDatabase.get(applicationContext).notificationDao()
        dao.deleteVersionsOlderThan(cutoff)
        dao.deleteEmptyThreads()
        return Result.success()
    }
}
