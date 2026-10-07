package com.example.notificationlog.data

import android.content.Context

class Preferences(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val excludedPackagesKey = "excludedPackages"

    fun isExcluded(packageName: String): Boolean =
        p.getStringSet(excludedPackagesKey, emptySet())?.contains(packageName) == true

    fun excludedPackages(): Set<String> =
        p.getStringSet(excludedPackagesKey, emptySet())?.toSet() ?: emptySet()

    fun setExcluded(packageName: String, excluded: Boolean) {
        val current = p.getStringSet(excludedPackagesKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (excluded) current.add(packageName) else current.remove(packageName)
        p.edit().putStringSet(excludedPackagesKey, current).apply()
    }

    var retentionDays: Int
        get() = p.getInt("retentionDays", 30)
        set(value) = p.edit().putInt("retentionDays", value.coerceIn(1, 3650)).apply()
}
