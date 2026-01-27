package com.example.mindcontrol

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Utils {

    private const val PREFS_NAME = "mindcontrol_prefs"
    private const val KEY_ALLOWED_APPS = "allowed_apps"
    private const val KEY_TIMER_END_TIME = "timer_end_time"
    private const val KEY_IS_LOCKED = "is_locked"
    
    // Broadcast Actions
    const val ACTION_TIMER_TICK = "com.example.mindcontrol.TIMER_TICK"
    const val ACTION_TIMER_FINISHED = "com.example.mindcontrol.TIMER_FINISHED"
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

    fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
