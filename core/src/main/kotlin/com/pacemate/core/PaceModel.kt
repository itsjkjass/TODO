package com.pacemate.core

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * What the app has learned about how fast this particular human works.
 *
 * `factor` is "actual minutes / average-person minutes". It starts from the
 * user's self-rating and is updated from the logger every time a session is
 * finished, so plans drift towards the user's real speed.
 */
@Serializable
data class PaceStats(
    val factor: Double = 1.0,
    val samples: Int = 0,
    val categoryFactors: Map<String, Double> = emptyMap(),
    val categorySamples: Map<String, Int> = emptyMap(),
)

object PaceModel {
    const val MIN_FACTOR = 0.5
    const val MAX_FACTOR = 2.5

    /** People underestimate; every estimate gets this much breathing room. */
    const val PLANNING_BUFFER = 0.15

    /** Getting into a task (opening files, finding notes, re-reading) costs a few minutes. */
    const val WARMUP_MINUTES = 5

    /** Weight of the newest observation when learning pace. */
    private const val LEARNING_RATE = 0.25

    /** Category-specific pace is used once we have this many samples. */
    private const val MIN_CATEGORY_SAMPLES = 3

    fun initialStats(pace: SelfRatedPace) = PaceStats(factor = pace.factor)

    fun factorFor(stats: PaceStats, category: String): Double {
        val n = stats.categorySamples[category] ?: 0
        val cat = stats.categoryFactors[category]
        return if (cat != null && n >= MIN_CATEGORY_SAMPLES) cat else stats.factor
    }

    /**
     * Realistic minutes this user needs for a subtask, before splitting into
     * sessions: average estimate x personal pace x planning buffer, plus
     * extra time for demanding work (humans slow down on hard tasks).
     */
    fun realisticMinutes(estimateMinutes: Int, difficulty: Int, stats: PaceStats, category: String): Int {
        val difficultyFactor = when (difficulty) {
            1 -> 0.95
            3 -> 1.15
            else -> 1.0
        }
        val raw = estimateMinutes * factorFor(stats, category) * difficultyFactor * (1 + PLANNING_BUFFER)
        return roundUpTo5(raw)
    }

    /**
     * Splits [totalMinutes] of work into sessions no longer than [focusBlock],
     * each including warm-up time. Tiny leftovers are merged into the last
     * session instead of creating a pointless 5-minute block.
     */
    fun splitIntoSessions(totalMinutes: Int, focusBlock: Int): List<Int> {
        if (totalMinutes <= 0) return emptyList()
        val block = focusBlock.coerceAtLeast(15)
        val workPerSession = (block - WARMUP_MINUTES).coerceAtLeast(10)
        val count = ceil(totalMinutes.toDouble() / workPerSession).toInt().coerceAtLeast(1)
        // Spread evenly: 100 min with 45-min sessions -> 34/33/33 rather than 45/45/10.
        val perSession = ceil(totalMinutes.toDouble() / count).toInt()
        val sessions = MutableList(count) { roundUpTo5((perSession + WARMUP_MINUTES).toDouble()).coerceAtMost(block) }
        // Never return a session shorter than 15 minutes.
        return sessions.map { it.coerceAtLeast(15) }
    }

    /**
     * Learns from a finished session. [plannedMinutes] already contains the
     * old pace factor, so we divide it back out to compare against the
     * average-person baseline.
     */
    fun learn(stats: PaceStats, category: String, plannedMinutes: Int, actualSeconds: Long): PaceStats {
        if (plannedMinutes <= 0 || actualSeconds < 120) return stats // ignore accidental taps
        val usedFactor = factorFor(stats, category)
        val baseline = plannedMinutes / usedFactor
        val observed = ((actualSeconds / 60.0) / baseline).coerceIn(MIN_FACTOR, MAX_FACTOR)

        val global = (stats.factor * (1 - LEARNING_RATE) + observed * LEARNING_RATE).coerceIn(MIN_FACTOR, MAX_FACTOR)
        val oldCat = stats.categoryFactors[category] ?: stats.factor
        val cat = (oldCat * (1 - LEARNING_RATE) + observed * LEARNING_RATE).coerceIn(MIN_FACTOR, MAX_FACTOR)
        return stats.copy(
            factor = global,
            samples = stats.samples + 1,
            categoryFactors = stats.categoryFactors + (category to cat),
            categorySamples = stats.categorySamples + (category to (stats.categorySamples[category] ?: 0) + 1),
        )
    }

    fun describe(stats: PaceStats): String {
        val pct = ((stats.factor - 1.0) * 100).roundToInt()
        val speed = when {
            pct > 5 -> "You take about $pct% longer than a typical estimate"
            pct < -5 -> "You work about ${-pct}% faster than a typical estimate"
            else -> "You work at about the typical pace"
        }
        return if (stats.samples == 0) "$speed (from your self-rating; the logger will fine-tune this)."
        else "$speed, learned from ${stats.samples} logged sessions."
    }

    private fun roundUpTo5(v: Double): Int = (ceil(v / 5.0) * 5).toInt()
}
