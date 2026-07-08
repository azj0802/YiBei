package com.example.yibei.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import java.util.Random as JavaRandom

/**
 * 模拟数据生成器
 * 用于生成30天的饮水、睡眠模拟数据，写入 SharedPreferences
 * 步数保持原样（从 MIUI ContentProvider 实时读取）
 */
object MockDataGenerator {

    private const val WATER_PREFS = "water_prefs"
    private const val SLEEP_PREFS = "sleep_manual"

    /**
     * 生成30天模拟数据（从 startDate 到 startDate+29天）
     *
     * @param context Android Context
     * @param startDate 起始日期（含），默认30天前
     */
    fun generateMockData(context: Context, startDate: LocalDate = LocalDate.now().minusDays(29)) {
        generateWaterData(context, startDate)
        generateSleepData(context, startDate)
    }

    // ==================== 喝水数据 ====================

    /**
     * 生成喝水模拟数据
     * - 日均 1800-2200ml，趋于平稳
     * - 偶有小幅浮动 ±300ml
     */
    private fun generateWaterData(context: Context, startDate: LocalDate) {
        val prefs = context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // 基准值：2000ml，围绕此值平稳波动
        val baseAmount = 2000
        var prevAmount = baseAmount

        for (i in 0 until 30) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString() // yyyy-MM-dd

            // 平稳波动：80%概率小幅波动（±200ml），20%概率较大浮动（±300ml）
            val amount = if (Random.nextFloat() < 0.8f) {
                // 小幅波动，趋向基准值（均值回归）
                val drift = (baseAmount - prevAmount) * 0.3
                val noise = Random.nextInt(-200, 201)
                (prevAmount + drift + noise).toInt().coerceIn(1500, 2500)
            } else {
                // 较大浮动
                val direction = if (Random.nextBoolean()) 1 else -1
                (prevAmount + direction * Random.nextInt(200, 401)).toInt().coerceIn(1500, 2500)
            }

            prevAmount = amount

            // 写入饮水量
            editor.putInt("amount_$dateStr", amount)

            // 生成模拟的喝水记录（3-8次/天）
            val recordCount = Random.nextInt(3, 9)
            editor.putInt("record_count_$dateStr", recordCount)

            var remaining = amount
            val times = generateDrinkTimes(recordCount)
            for (j in 0 until recordCount) {
                val isLast = (j == recordCount - 1)
                val recordAmount = if (isLast) remaining else {
                    val maxPerDrink = remaining / (recordCount - j)
                    Random.nextInt(100, maxPerDrink.coerceAtMost(500) + 1)
                }
                remaining -= recordAmount
                editor.putString("record_time_${dateStr}_$j", times[j])
                editor.putInt("record_amount_${dateStr}_$j", recordAmount)
            }
        }

        editor.apply()
    }

    /**
     * 生成合理的喝水时间点
     * 分布在 07:00-22:00 之间
     */
    private fun generateDrinkTimes(count: Int): List<String> {
        val slots = mutableListOf<Int>() // 小时 * 60 + 分钟
        val startMin = 7 * 60   // 07:00
        val endMin = 22 * 60    // 22:00
        val range = endMin - startMin

        for (i in 0 until count) {
            val progress = (i + 1).toFloat() / (count + 1)
            val baseTime = startMin + (progress * range).toInt()
            // 加入随机偏移 ±30分钟
            val jitter = Random.nextInt(-30, 31)
            slots.add((baseTime + jitter).coerceIn(startMin, endMin))
        }

        return slots.sorted().map { min ->
            String.format("%02d:%02d", min / 60, min % 60)
        }
    }

    // ==================== 睡眠数据 ====================

    /**
     * 生成睡眠模拟数据
     * - 夜间睡眠：日均 6.5-8 小时（390-480 分钟）
     * - 午睡：0-1 小时（0-60 分钟），偶尔有
     * - 趋于平稳，作息规律
     */
    private fun generateSleepData(context: Context, startDate: LocalDate) {
        val prefs = context.getSharedPreferences(SLEEP_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // 基准入睡/起床时间
        var baseSleepHour = 23  // 23:00 入睡
        var baseWakeHour = 7    // 07:00 起床

        for (i in 0 until 30) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString()

            // 夜间睡眠时长：6.5-8小时，平稳波动
            val nightDurationMin = generateStableValue(420, 480, 30) // 7h±30min范围

            // 入睡时间微调（±45分钟）
            val sleepJitter = Random.nextInt(-45, 46)
            val sleepMinute = Random.nextInt(0, 59)
            val sleepHour = (baseSleepHour + sleepJitter / 60).coerceIn(21, 24)
            val actualSleepMin = (sleepMinute + sleepJitter % 60 + 60) % 60

            // 计算起床时间
            val wakeDurationMs = nightDurationMin * 60_000L
            val sleepDate = if (sleepHour >= 12) date.minusDays(1) else date
            val startMs = sleepDate.atTime(sleepHour, actualSleepMin)
                .toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
            val endMs = startMs + wakeDurationMs

            // 写入夜间睡眠数据
            editor.putString("sleep_$dateStr", "$startMs,$endMs")

            // 更新基准时间（缓慢漂移）
            baseSleepHour = ((baseSleepHour * 0.9 + sleepHour * 0.1)).toInt().coerceIn(21, 24)
            baseWakeHour = ((baseWakeHour * 0.9 + (sleepHour + nightDurationMin / 60) % 24 * 0.1)).toInt()
        }

        editor.apply()
    }

    /**
     * 生成稳定值：围绕基准值小幅波动
     *
     * @param base 基准值
     * @param maxRange 最大范围（±maxRange/2）
     * @param stability 稳定性系数，越大越稳定
     */
    private fun generateStableValue(base: Int, maxRange: Int, stability: Int): Int {
        val halfRange = maxRange / 2
        // 大部分值集中在基准附近（正态分布近似）
        val value = base + (JavaRandom().nextGaussian() * stability / 2).toInt()
        return value.coerceIn(base - halfRange, base + halfRange)
    }

    // ==================== 验证方法 ====================

    /**
     * 验证生成的数据是否正确写入
     *
     * @return 验证结果字符串
     */
    fun verifyData(context: Context, startDate: LocalDate = LocalDate.now().minusDays(29)): String {
        val waterPrefs = context.getSharedPreferences(WATER_PREFS, Context.MODE_PRIVATE)
        val sleepPrefs = context.getSharedPreferences(SLEEP_PREFS, Context.MODE_PRIVATE)

        val sb = StringBuilder()
        sb.appendLine("=== 模拟数据验证 ===")
        sb.appendLine()

        // 抽查3天数据
        val checkDays = listOf(0, 14, 29)
        for (dayOffset in checkDays) {
            val date = startDate.plusDays(dayOffset.toLong())
            val dateStr = date.toString()

            sb.appendLine("--- ${dateStr} ---")

            // 喝水
            val water = waterPrefs.getInt("amount_$dateStr", -1)
            val recordCount = waterPrefs.getInt("record_count_$dateStr", -1)
            sb.appendLine("  喝水: ${water}ml (${recordCount}条记录)")

            // 睡眠
            val sleepRaw = sleepPrefs.getString("sleep_$dateStr", null)
            if (sleepRaw != null) {
                val parts = sleepRaw.split(",")
                if (parts.size == 2) {
                    val startMs = parts[0].toLong()
                    val endMs = parts[1].toLong()
                    val durationMin = (endMs - startMs) / 60_000
                    val h = durationMin / 60
                    val m = durationMin % 60
                    sb.appendLine("  睡眠: ${h}h${m}m")
                }
            } else {
                sb.appendLine("  睡眠: 无数据")
            }
            sb.appendLine()
        }

        // 统计摘要
        var totalWater = 0
        var validWaterDays = 0
        var validSleepDays = 0
        var totalSleepMin = 0L

        for (i in 0 until 30) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.toString()

            val w = waterPrefs.getInt("amount_$dateStr", 0)
            if (w > 0) {
                totalWater += w
                validWaterDays++
            }

            val s = sleepPrefs.getString("sleep_$dateStr", null)
            if (s != null) {
                val parts = s.split(",")
                if (parts.size == 2) {
                    totalSleepMin += (parts[1].toLong() - parts[0].toLong()) / 60_000
                    validSleepDays++
                }
            }
        }

        sb.appendLine("=== 30天统计 ===")
        sb.appendLine("  喝水日均: ${if (validWaterDays > 0) totalWater / validWaterDays else 0}ml")
        sb.appendLine("  有效天数: $validWaterDays/30")
        if (validSleepDays > 0) {
            val avgH = totalSleepMin / validSleepDays / 60
            val avgM = (totalSleepMin / validSleepDays) % 60
            sb.appendLine("  睡眠日均: ${avgH}h${avgM}m")
        }
        sb.appendLine("  睡眠有效天数: $validSleepDays/30")

        return sb.toString()
    }
}
