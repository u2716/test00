package chimahon.dictionary.ui

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatisticsHelper {
    private const val MAX_RECENT_HOURS = 48L

    @Synchronized
    fun recordLookup(prefs: DictionaryPreferences) {
        val now = System.currentTimeMillis()

        // 1. Update total
        val total = prefs.statsTotalLookups().get()
        prefs.statsTotalLookups().set(total + 1)

        // 2. Update recent timestamps
        val recentJson = prefs.statsRecentTimestamps().get()
        val timestamps = mutableListOf<Long>()
        if (recentJson.isNotBlank()) {
            val arr = JSONArray(recentJson)
            for (i in 0 until arr.length()) {
                timestamps.add(arr.getLong(i))
            }
        }
        timestamps.add(now)

        // 3. Prune old timestamps into daily counts
        val dailyJson = prefs.statsDailyCounts().get()
        val dailyCounts = if (dailyJson.isNotBlank()) JSONObject(dailyJson) else JSONObject()
        
        val cutoff = now - (MAX_RECENT_HOURS * 60 * 60 * 1000)
        val pruned = mutableListOf<Long>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (ts in timestamps) {
            if (ts < cutoff) {
                val dateStr = sdf.format(Date(ts))
                val count = dailyCounts.optInt(dateStr, 0)
                dailyCounts.put(dateStr, count + 1)
            } else {
                pruned.add(ts)
            }
        }

        // Save back
        val arr = JSONArray()
        for (ts in pruned) arr.put(ts)
        prefs.statsRecentTimestamps().set(arr.toString())
        prefs.statsDailyCounts().set(dailyCounts.toString())
    }

    data class Stats(
        val total: Int,
        val last24h: Int,
        val last7d: Int,
        val last30d: Int,
        val last365d: Int
    )

    @Synchronized
    fun getStats(prefs: DictionaryPreferences): Stats {
        val now = System.currentTimeMillis()
        
        // Force a prune to ensure daily counts are up to date for the UI
        pruneIfNeeded(prefs, now)

        val total = prefs.statsTotalLookups().get()

        val recentJson = prefs.statsRecentTimestamps().get()
        val timestamps = mutableListOf<Long>()
        if (recentJson.isNotBlank()) {
            val arr = JSONArray(recentJson)
            for (i in 0 until arr.length()) {
                timestamps.add(arr.getLong(i))
            }
        }

        val dailyJson = prefs.statsDailyCounts().get()
        val dailyCounts = if (dailyJson.isNotBlank()) JSONObject(dailyJson) else JSONObject()

        val dayMs = 24 * 60 * 60 * 1000L
        val cutoff24h = now - dayMs
        val cutoff7d = now - 7 * dayMs
        val cutoff30d = now - 30 * dayMs
        val cutoff365d = now - 365 * dayMs

        var last24h = 0
        var last7d = 0
        var last30d = 0
        var last365d = 0

        // Count from recent timestamps
        for (ts in timestamps) {
            if (ts >= cutoff24h) last24h++
            if (ts >= cutoff7d) last7d++
            if (ts >= cutoff30d) last30d++
            if (ts >= cutoff365d) last365d++
        }

        // Count from daily map
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val keys = dailyCounts.keys()
        while (keys.hasNext()) {
            val dateStr = keys.next()
            try {
                val date = sdf.parse(dateStr) ?: continue
                val count = dailyCounts.getInt(dateStr)
                val ts = date.time
                if (ts >= cutoff7d - dayMs) last7d += count
                if (ts >= cutoff30d - dayMs) last30d += count
                if (ts >= cutoff365d - dayMs) last365d += count
            } catch (_: Exception) {}
        }

        return Stats(total, last24h, last7d, last30d, last365d)
    }

    private fun pruneIfNeeded(prefs: DictionaryPreferences, now: Long) {
        val recentJson = prefs.statsRecentTimestamps().get()
        if (recentJson.isBlank()) return
        
        val timestamps = mutableListOf<Long>()
        val arr = JSONArray(recentJson)
        for (i in 0 until arr.length()) timestamps.add(arr.getLong(i))
        
        val cutoff = now - (MAX_RECENT_HOURS * 60 * 60 * 1000)
        val toPrune = timestamps.filter { it < cutoff }
        if (toPrune.isEmpty()) return

        val dailyJson = prefs.statsDailyCounts().get()
        val dailyCounts = if (dailyJson.isNotBlank()) JSONObject(dailyJson) else JSONObject()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (ts in toPrune) {
            val dateStr = sdf.format(Date(ts))
            dailyCounts.put(dateStr, dailyCounts.optInt(dateStr, 0) + 1)
        }

        val pruned = timestamps.filter { it >= cutoff }
        val newArr = JSONArray()
        for (ts in pruned) newArr.put(ts)
        prefs.statsRecentTimestamps().set(newArr.toString())
        prefs.statsDailyCounts().set(dailyCounts.toString())
    }
}
