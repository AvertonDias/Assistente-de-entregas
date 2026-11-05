package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CpfApiUsageState(
    val dailyLimit: Int = 100,
    val totalLookups: Int = 0,
    val todayLookups: Int = 0,
    val remainingCredits: Int? = null,
    val lastLookupTime: String = ""
) {
    val remainingToday: Int
        get() = (dailyLimit - todayLookups).coerceAtLeast(0)

    val progressFraction: Float
        get() = (todayLookups.toFloat() / dailyLimit.toFloat()).coerceIn(0f, 1f)
}

object CpfApiUsageTracker {
    private const val PREFS_NAME = "cpf_api_usage_prefs"
    private const val KEY_TOTAL = "total_lookups"
    private const val KEY_TODAY_COUNT = "today_lookups"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_REMAINING_CREDITS = "remaining_credits"
    private const val KEY_LAST_LOOKUP_TIME = "last_lookup_time"
    private const val KEY_DAILY_LIMIT = "daily_limit"

    const val DEFAULT_DAILY_LIMIT = 100

    private val _usageState = MutableStateFlow(CpfApiUsageState(dailyLimit = DEFAULT_DAILY_LIMIT))
    val usageState: StateFlow<CpfApiUsageState> = _usageState

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""

        val limit = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
        val total = prefs.getInt(KEY_TOTAL, 0)
        var today = prefs.getInt(KEY_TODAY_COUNT, 0)
        if (lastDate != todayStr) {
            today = 0
            prefs.edit().putString(KEY_LAST_DATE, todayStr).putInt(KEY_TODAY_COUNT, 0).apply()
        }

        val credits = if (prefs.contains(KEY_REMAINING_CREDITS)) prefs.getInt(KEY_REMAINING_CREDITS, -1).takeIf { it >= 0 } else null
        val lastTime = prefs.getString(KEY_LAST_LOOKUP_TIME, "") ?: ""

        _usageState.value = CpfApiUsageState(
            dailyLimit = limit,
            totalLookups = total,
            todayLookups = today,
            remainingCredits = credits,
            lastLookupTime = lastTime
        )
    }

    fun setDailyLimit(context: Context, newLimit: Int) {
        val validLimit = newLimit.coerceAtLeast(1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DAILY_LIMIT, validLimit).apply()
        _usageState.value = _usageState.value.copy(dailyLimit = validLimit)
    }

    fun recordLookup(context: Context, creditsRemainingFromHeader: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        val limit = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

        var total = prefs.getInt(KEY_TOTAL, 0) + 1
        var today = prefs.getInt(KEY_TODAY_COUNT, 0)
        if (lastDate != todayStr) {
            today = 1
        } else {
            today += 1
        }

        val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val editor = prefs.edit()
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_TODAY_COUNT, today)
            .putString(KEY_LAST_DATE, todayStr)
            .putString(KEY_LAST_LOOKUP_TIME, nowTimeStr)

        val finalCredits = creditsRemainingFromHeader ?: run {
            if (prefs.contains(KEY_REMAINING_CREDITS)) {
                val old = prefs.getInt(KEY_REMAINING_CREDITS, -1)
                if (old > 0) old - 1 else if (old == 0) 0 else null
            } else null
        }

        if (finalCredits != null) {
            editor.putInt(KEY_REMAINING_CREDITS, finalCredits)
        }
        editor.apply()

        _usageState.value = CpfApiUsageState(
            dailyLimit = limit,
            totalLookups = total,
            todayLookups = today,
            remainingCredits = finalCredits,
            lastLookupTime = nowTimeStr
        )
    }
}
