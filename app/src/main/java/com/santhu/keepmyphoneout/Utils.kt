package com.santhu.keepmyphoneout

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Utils {

    private const val PREFS_NAME = "keep_my_phone_out_prefs"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_TIMER_END_TIME = "timer_end_time"
    private const val KEY_IS_LOCKED = "is_locked"
    private const val KEY_SESSION_HISTORY = "session_history_v2"
    
    data class SessionRecord(
        val durationMinutes: Int,
        val allowedApps: List<String>,
        val timestamp: Long,
        val dateString: String
    )
    
    // Broadcast Actions
    const val ACTION_TIMER_TICK = "com.santhu.keepmyphoneout.TIMER_TICK"
    const val ACTION_TIMER_FINISHED = "com.santhu.keepmyphoneout.TIMER_FINISHED"
    const val EXTRA_TIME_REMAINING = "time_remaining"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setAllowedApps(context: Context, apps: Set<String>) {
        val gson = Gson()
        val json = gson.toJson(apps)
        getPrefs(context).edit().putString(KEY_ALLOWED_APPS, json).apply()
    }

    fun getAllowedApps(context: Context): Set<String> {
        val json = getPrefs(context).getString(KEY_ALLOWED_APPS, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun clearAllowedApps(context: Context) {
        getPrefs(context).edit().remove(KEY_ALLOWED_APPS).apply()
    }

    private const val KEY_TIMER_TOTAL_DURATION = "timer_total_duration"

    // ...

    fun setTimer(context: Context, endTime: Long, totalDuration: Long) {
        getPrefs(context).edit()
            .putLong(KEY_TIMER_END_TIME, endTime)
            .putLong(KEY_TIMER_TOTAL_DURATION, totalDuration)
            .putBoolean(KEY_IS_LOCKED, true)
            .apply()
    }

    fun getTotalDuration(context: Context): Long {
        return getPrefs(context).getLong(KEY_TIMER_TOTAL_DURATION, 0)
    }

    fun getEndTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_TIMER_END_TIME, 0)
    }

    fun isLocked(context: Context): Boolean {
        // Double check time validity
        val locked = getPrefs(context).getBoolean(KEY_IS_LOCKED, false)
        if (locked) {
            val endTime = getEndTime(context)
            if (System.currentTimeMillis() > endTime) {
                unlock(context)
                return false
            }
        }
        return locked
    }

    fun unlock(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_LOCKED, false)
            .remove(KEY_TIMER_END_TIME)
            .apply()
    }

    private const val KEY_TOTAL_HISTORY = "history_total_time"

    fun addToTotalHistory(context: Context, durationMillis: Long) {
        val current = getTotalHistory(context)
        getPrefs(context).edit().putLong(KEY_TOTAL_HISTORY, current + durationMillis).apply()
    }

    fun getTotalHistory(context: Context): Long {
        return getPrefs(context).getLong(KEY_TOTAL_HISTORY, 0)
    }

    fun addSessionToHistory(context: Context, durationMinutes: Int) {
        val prefs = getPrefs(context)
        val historyJson = prefs.getString(KEY_SESSION_HISTORY, "[]")
        val type = object : TypeToken<MutableList<SessionRecord>>() {}.type
        val history: MutableList<SessionRecord> = Gson().fromJson(historyJson, type)
        
        val allowedApps = getAllowedApps(context).toList()
        val timestamp = System.currentTimeMillis()
        val date = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        
        val record = SessionRecord(durationMinutes, allowedApps, timestamp, date)
        history.add(0, record) // Add to top
        
        // Keep last 50
        if (history.size > 50) history.removeAt(history.size - 1)
        
        prefs.edit().putString(KEY_SESSION_HISTORY, Gson().toJson(history)).apply()
    }

    fun getSessionHistoryRecords(context: Context): List<SessionRecord> {
        val json = getPrefs(context).getString(KEY_SESSION_HISTORY, "[]")
        val type = object : TypeToken<List<SessionRecord>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun getSessionHistory(context: Context): List<String> {
        return getSessionHistoryRecords(context).map { "${it.durationMinutes} mins on ${it.dateString}" }
    }

    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_SESSION_HISTORY).apply()
    }

    fun removeHistoryItem(context: Context, timestamp: Long) {
        val history = getSessionHistoryRecords(context).toMutableList()
        history.removeAll { it.timestamp == timestamp }
        getPrefs(context).edit().putString(KEY_SESSION_HISTORY, Gson().toJson(history)).apply()
    }

    fun formatHistoryTime(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return "${hours}h ${minutes}m"
    }

    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    // ===== Onboarding & Usage Tracking =====
    
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_ESTIMATED_USAGE = "estimated_usage_minutes"
    private const val KEY_ACTUAL_USAGE = "actual_usage_minutes"
    
    fun isOnboardingCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    
    fun setEstimatedUsage(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_ESTIMATED_USAGE, minutes).apply()
    }
    
    fun getEstimatedUsage(context: Context): Int {
        return getPrefs(context).getInt(KEY_ESTIMATED_USAGE, 0)
    }
    
    fun setActualUsage(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_ACTUAL_USAGE, minutes).apply()
    }
    
    fun getActualUsage(context: Context): Int {
        return getPrefs(context).getInt(KEY_ACTUAL_USAGE, 0)
    }
    
    fun getTimeSavedToday(context: Context): Long {
        // Calculate time saved based on focus sessions completed today
        // This is a simplified version - you could make it more sophisticated
        return getTotalHistory(context)
    }
    
    fun formatMinutesToReadable(minutes: Int): String {
        return if (minutes < 60) {
            "${minutes}m"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
    }
}
