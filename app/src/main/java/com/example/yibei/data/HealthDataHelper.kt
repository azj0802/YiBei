package com.example.yibei.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object HealthDataHelper {

    private const val TAG = "HealthDataHelper"

    // ==================== 数据类 ====================

    data class NightSleep(
        val durationMinutes: Long,
        val startTime: Instant,
        val endTime: Instant,
        val awakeSegments: List<AwakeGap>
    )

    data class NapSleep(
        val durationMinutes: Long,
        val startTime: Instant,
        val endTime: Instant
    )

    data class AwakeGap(
        val startTime: Instant,
        val durationMinutes: Long
    )

    enum class SleepSource { MANUAL, INFERRED, NONE }

    data class SleepResult(
        val night: NightSleep?,
        val nap: NapSleep?,
        val source: SleepSource
    )

    // ==================== 步数（MIUI ContentProvider） ====================

    private const val MIUI_STEPS_URI = "content://com.miui.providers.steps/item"

    fun readSteps(context: Context, date: LocalDate): Long {
        return try {
            val uri = Uri.parse(MIUI_STEPS_URI)
            val startMs = date.atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
            val endMs = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

            val projection = arrayOf("_id", "_begin_time", "_end_time", "_mode", "_steps")
            val selection = "_begin_time >= ? AND _end_time <= ?"
            val args = arrayOf(startMs.toString(), endMs.toString())

            val cursor = context.contentResolver.query(uri, projection, selection, args, null)
            var total = 0L
            cursor?.use {
                val colIdx = it.getColumnIndex("_steps")
                while (it.moveToNext()) {
                    total += it.getLong(colIdx)
                }
            }
            total
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading steps", e)
            0L
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps: ${e.message}", e)
            0L
        }
    }

    // ==================== 睡眠推断 ====================

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private const val NIGHT_START_HOUR = 20
    private const val NIGHT_END_HOUR = 10
    private const val NAP_START_HOUR = 13
    private const val NAP_END_HOUR = 16

    // 参数常量
    private const val SHORT_AWAKE_MAX_MS = 5 * 60 * 1000L       // 5 分钟
    private const val LONG_SLEEP_MIN_MS = 30 * 60 * 1000L       // 30 分钟
    private const val INTERRUPT_MIN_MS = 15 * 60 * 1000L        // 15 分钟
    private const val NAP_MIN_MS = 20 * 60 * 1000L              // 20 分钟

    private fun nightStartMs(date: LocalDate): Long =
        date.minusDays(1).atTime(NIGHT_START_HOUR, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun nightEndMs(date: LocalDate): Long =
        date.atTime(NIGHT_END_HOUR, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun napStartMs(date: LocalDate): Long =
        date.atTime(NAP_START_HOUR, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    private fun napEndMs(date: LocalDate): Long =
        date.atTime(NAP_END_HOUR, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    // ==================== 手动睡眠数据 ====================

    private fun sleepManualKey(date: LocalDate): String = "sleep_${date.format(dateFormatter)}"

    fun getSleepManual(prefs: SharedPreferences, date: LocalDate): NightSleep? {
        val raw = prefs.getString(sleepManualKey(date), null) ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        return try {
            val startMs = parts[0].toLong()
            val endMs = parts[1].toLong()
            if (endMs <= startMs) return null
            NightSleep((endMs - startMs) / 60_000, Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs), emptyList())
        } catch (_: Exception) { null }
    }

    fun saveSleepManual(prefs: SharedPreferences, date: LocalDate, startMs: Long, endMs: Long) {
        prefs.edit().putString(sleepManualKey(date), "$startMs,$endMs").apply()
    }

    fun clearSleepManual(prefs: SharedPreferences, date: LocalDate) {
        prefs.edit().remove(sleepManualKey(date)).apply()
    }

    // ==================== 睡眠推断 ====================

    fun getSleepResult(context: Context, prefs: SharedPreferences, date: LocalDate): SleepResult {
        val manual = getSleepManual(prefs, date)
        if (manual != null) {
            val nap = inferNap(context, date)
            return SleepResult(manual, nap, SleepSource.MANUAL)
        }
        val night = inferNightSleep(context, date)
        val nap = inferNap(context, date)
        return SleepResult(night, nap, if (night != null) SleepSource.INFERRED else SleepSource.NONE)
    }

    private fun collectScreenEvents(usm: UsageStatsManager, startMs: Long, endMs: Long): List<Long> {
        val timestamps = mutableListOf<Long>()
        val events = usm.queryEvents(startMs, endMs)
        val ev = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                ev.eventType == android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE
            ) {
                timestamps.add(ev.timeStamp)
            }
        }
        timestamps.sort()
        return timestamps
    }

    fun inferNightSleep(context: Context, date: LocalDate): NightSleep? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startMs = nightStartMs(date)
            val endMs = nightEndMs(date)

            val ts = collectScreenEvents(usm, startMs, endMs)

            if (ts.isEmpty()) {
                val dur = (endMs - startMs) / 60_000
                if (dur < 60) return null
                return NightSleep(dur, Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs), emptyList())
            }

            data class Gap(val start: Long, val end: Long, val ms: Long)

            val gaps = mutableListOf<Gap>()
            gaps.add(Gap(startMs, ts[0], ts[0] - startMs))
            for (i in 0 until ts.size - 1) {
                gaps.add(Gap(ts[i], ts[i + 1], ts[i + 1] - ts[i]))
            }
            gaps.add(Gap(ts.last(), endMs, endMs - ts.last()))

            val best = gaps.maxByOrNull { it.ms } ?: return null
            val durMin = best.ms / 60_000
            if (durMin < 60) return null

            val awakeGaps = mutableListOf<AwakeGap>()
            for (i in ts.indices) {
                val t = ts[i]
                if (t <= best.start || t >= best.end) continue

                val prev = if (i > 0) ts[i - 1] else best.start
                val next = if (i < ts.size - 1) ts[i + 1] else best.end
                val beforeMs = t - prev
                val afterMs = next - t

                if (beforeMs <= SHORT_AWAKE_MAX_MS && afterMs >= LONG_SLEEP_MIN_MS) continue
                if (beforeMs >= INTERRUPT_MIN_MS && afterMs >= INTERRUPT_MIN_MS) {
                    awakeGaps.add(AwakeGap(Instant.ofEpochMilli(t), Math.min(beforeMs, afterMs) / 60_000))
                }
            }

            NightSleep(durMin, Instant.ofEpochMilli(best.start), Instant.ofEpochMilli(best.end), awakeGaps)
        } catch (e: Exception) {
            null
        }
    }

    fun inferNap(context: Context, date: LocalDate): NapSleep? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val startMs = napStartMs(date)
            val endMs = napEndMs(date)

            val ts = collectScreenEvents(usm, startMs, endMs)

            if (ts.isEmpty()) {
                val dur = (endMs - startMs) / 60_000
                return if (dur >= NAP_MIN_MS / 60_000) NapSleep(dur, Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)) else null
            }

            var bestStart = startMs
            var bestEnd = startMs
            var bestMs = 0L

            // 起始间隙
            if (ts[0] - startMs >= NAP_MIN_MS && ts[0] - startMs > bestMs) {
                bestMs = ts[0] - startMs
                bestEnd = ts[0]
            }
            // 事件间间隙
            for (i in 0 until ts.size - 1) {
                val gap = ts[i + 1] - ts[i]
                if (gap >= NAP_MIN_MS && gap > bestMs) {
                    bestMs = gap
                    bestStart = ts[i]
                    bestEnd = ts[i + 1]
                }
            }
            // 末尾间隙
            if (endMs - ts.last() >= NAP_MIN_MS && endMs - ts.last() > bestMs) {
                bestMs = endMs - ts.last()
                bestStart = ts.last()
                bestEnd = endMs
            }

            if (bestMs == 0L) return null
            val durMin = bestMs / 60_000
            NapSleep(durMin, Instant.ofEpochMilli(bestStart), Instant.ofEpochMilli(bestEnd))
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 睡眠评分 ====================

    fun calcSleepScore(nightDurationMin: Long, awakeTotalMin: Long): Int {
        val durationScore = when {
            nightDurationMin >= 480 -> 60
            nightDurationMin >= 420 -> 55
            nightDurationMin >= 360 -> 50
            nightDurationMin >= 300 -> 40
            nightDurationMin >= 240 -> 30
            else -> 20
        }
        val awakeRatio = awakeTotalMin.toFloat() / nightDurationMin.coerceAtLeast(1)
        val awakePenalty = (awakeRatio * 40).toInt().coerceAtMost(40)
        return (durationScore + (40 - awakePenalty)).coerceIn(0, 100)
    }

    /** 读取指定日期的喝水量 (ml) */
    fun readWaterAmount(prefs: SharedPreferences, date: LocalDate): Int {
        val key = "amount_${date.format(dateFormatter)}"
        return prefs.getInt(key, 0)
    }

    /** 获取近N天喝水时间高峰（返回高峰时段描述，如"14:00-15:00"） */
    fun getWaterPeakHour(prefs: SharedPreferences, days: Int): String {
        val hourTotals = IntArray(24)
        val now = LocalDate.now()
        for (d in 0 until days) {
            val date = now.minusDays(d.toLong())
            val dateStr = date.format(dateFormatter)
            val count = prefs.getInt("record_count_$dateStr", 0)
            for (i in 0 until count) {
                val time = prefs.getString("record_time_${dateStr}_$i", "") ?: continue
                val amount = prefs.getInt("record_amount_${dateStr}_$i", 0)
                val hour = time.split(":")[0].toIntOrNull() ?: continue
                hourTotals[hour] += amount
            }
        }
        val peakHour = hourTotals.indices.maxByOrNull { hourTotals[it] } ?: return "--"
        if (hourTotals[peakHour] == 0) return "--"
        return String.format("%02d:00-%02d:00", peakHour, (peakHour + 1) % 24)
    }
}
