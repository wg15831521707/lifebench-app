package com.lifebench.app.util

import com.lifebench.app.data.entity.FitnessPlanEntity
import com.lifebench.app.data.entity.FitnessProfileEntity
import com.lifebench.app.data.entity.SleepEntity
import kotlin.math.roundToInt

/**
 * 业务计算工具：睡眠时长展示、睡眠建议、舒尔特星级评级、7 天健身计划生成。
 * 全部为纯函数，无副作用，便于单元测试与页面复用。
 */
object CalcUtil {

    /** 睡眠时长展示：如 "7h30m"。 */
    fun fmtSleep(min: Int): String {
        val h = min / 60
        val m = min % 60
        return "${h}h${m}m"
    }

    /**
     * 睡眠改善建议：根据近一周平均时长与规律性给出可执行建议。
     * recent 已按时间倒序（最新在前），这里只看最近的若干条。
     */
    fun sleepSuggestion(recent: List<SleepEntity>): String {
        if (recent.isEmpty()) {
            return "暂无足够数据，建议每天 22:30 前入睡、保证 7~8 小时睡眠，连续记录几天后这里会给出专属建议。"
        }
        val sample = recent.take(7)
        val avg = sample.map { it.durationMin }.average()
        val avgH = avg / 60.0
        return when {
            avgH < 6.5 ->
                "近一周平均睡眠仅 %.1f 小时，明显偏少。建议提前 30 分钟就寝，睡前 1 小时减少屏幕时间。".format(avgH)
            (sample.maxOf { it.durationMin } - sample.minOf { it.durationMin }) > 120 ->
                "睡眠时长波动较大（相差超 2 小时）。建议固定作息，周末也尽量不熬夜补觉。"
            avgH <= 8.0 ->
                "睡眠时长良好（平均 %.1f 小时）。保持规律作息、稳定入睡时间即可。".format(avgH)
            else ->
                "近一周平均睡眠 %.1f 小时，略偏多。注意避免白天过久午睡影响夜间入睡。".format(avgH)
        }
    }

    /**
     * 舒尔特方格星级评级（0~3 星）：依据该规格「最短用时」给即时、可感知的反馈，
     * 比抽象效率分更直观。阈值（秒）：★★★ ≤ n²×0.8；★★ ≤ n²×1.4；否则 ★。
     * 例：5×5 三星 ≤20s、二星 ≤35s。
     */
    fun schulteStars(size: Int, timeMs: Long): Int {
        if (timeMs <= 0) return 0
        val sec = timeMs / 1000.0
        val n2 = size * size
        return when {
            sec <= n2 * 0.8 -> 3
            sec <= n2 * 1.4 -> 2
            else -> 1
        }
    }

    /**
     * 生成 7 天个性化健身计划（dayIndex 0~6，第 6 天为恢复日）。
     * 依据运动基础（初级/中级/高级）与目标（减脂/增肌/塑形）调整强度与侧重。
     */
    fun generateFitnessPlan(p: FitnessProfileEntity): List<FitnessPlanEntity> {
        val lvl = when (p.level) {
            "中级" -> 2
            "高级" -> 3
            else -> 1
        }
        val moreCardio = p.goal == "减脂"      // 减脂侧重有氧
        val moreStrength = p.goal == "增肌"    // 增肌侧重力量
        val sets = lvl + 2
        val reps = when (lvl) { 1 -> 10; 2 -> 12; else -> 15 }
        val cardioMin = if (moreCardio) 30 + lvl * 5 else 20 + lvl * 3
        val stretchMin = if (p.goal == "塑形") 20 else 12

        // 同一天内同一动作：有组数则按 sets×reps×5 秒估算时长与热量；纯计时动作直接用 dur。
        fun mk(day: Int, name: String, s: Int, r: Int, dur: Int): FitnessPlanEntity {
            val minutes = if (dur > 0) dur else (s * r * 5) / 60
            return FitnessPlanEntity(
                dayIndex = day,
                actionName = name,
                sets = s,
                reps = r,
                durationMin = minutes,
                calories = minutes * 6,
            )
        }

        val plan = mutableListOf<FitnessPlanEntity>()
        // 周一：力量 + 核心
        plan += mk(0, "热身拉伸", 0, 0, stretchMin)
        plan += mk(0, if (moreStrength) "俯卧撑" else "深蹲", sets, reps, 0)
        plan += mk(0, "平板支撑", 0, 0, 60 + lvl * 20)
        // 周二：有氧
        plan += mk(1, "慢跑/快走", 0, 0, cardioMin)
        plan += mk(1, "跳绳", 0, 0, 10 + lvl * 3)
        // 周三：力量 + 柔韧
        plan += mk(2, "热身拉伸", 0, 0, stretchMin)
        plan += mk(2, "哑铃推举", sets, reps, 0)
        plan += mk(2, "弓步蹲", sets, reps, 0)
        // 周四：有氧
        plan += mk(3, "骑行/椭圆机", 0, 0, cardioMin)
        plan += mk(3, "高抬腿", 0, 0, 8 + lvl * 2)
        // 周五：力量 + 核心
        plan += mk(4, "热身拉伸", 0, 0, stretchMin)
        plan += mk(4, if (moreStrength) "引体向上" else "臀桥", sets, reps, 0)
        plan += mk(4, "卷腹", sets, 15, 0)
        // 周六：综合
        plan += mk(5, "户外徒步", 0, 0, cardioMin + 10)
        plan += mk(5, "瑜伽", 0, 0, 20)
        // 周日：恢复
        plan += mk(6, "散步放松", 0, 0, 20)
        plan += mk(6, "全身拉伸", 0, 0, stretchMin)
        return plan
    }
}
