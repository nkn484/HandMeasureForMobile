package com.resources.handqualitygate.ringsize

import kotlin.math.sqrt

data class SizeResult(
    val mmPerPx: Double,
    val fingerWidthMm: Double,
    val ringSizeSuggestion: String,
    val confidence: Float,
    val reasonsFail: List<String>,
    val debugMetrics: Map<String, Any>,
)

class SizeAggregator(
    private val cardMinConfidence: Float = 0.75f,
    private val handMinConfidence: Float = 0.65f,
    private val minValidFrames: Int = 3,
    private val stableFrames: Int = 6,
) {
    fun aggregate(
        measurements: List<FrameMeasurement>,
    ): SizeResult {
        val reasons = mutableListOf<String>()
        val valid =
            measurements.filter {
                it.cardConfidence >= cardMinConfidence && it.handConfidence >= handMinConfidence
            }

        if (valid.size < minValidFrames) {
            reasons += "CARD_NOT_FOUND"
            reasons += "HAND_NOT_STABLE"
            return SizeResult(
                mmPerPx = 0.0,
                fingerWidthMm = 0.0,
                ringSizeSuggestion = "N/A",
                confidence = 0.1f,
                reasonsFail = reasons,
                debugMetrics = mapOf("validFrames" to valid.size),
            )
        }

        val widths = valid.map { it.widthMm }.sorted()
        val median = widths[widths.size / 2]
        val mean = widths.average()
        val variance = widths.map { (it - mean) * (it - mean) }.average()
        val stddev = sqrt(variance)

        if (valid.size < stableFrames) {
            reasons += "NOT_ENOUGH_STABLE_FRAMES"
        }

        val confCount = (valid.size / (stableFrames.toFloat())).coerceIn(0f, 1f)
        val confStd = (1.0 - (stddev / 2.0)).toFloat().coerceIn(0f, 1f)
        val confCard = valid.map { it.cardConfidence }.average().toFloat().coerceIn(0f, 1f)
        val confHand = valid.map { it.handConfidence }.average().toFloat().coerceIn(0f, 1f)

        val confidence =
            (0.35f * confCount + 0.25f * confStd + 0.2f * confCard + 0.2f * confHand)
                .coerceIn(0f, 1f)

        val ringSize = estimateRingSize(median)

        return SizeResult(
            mmPerPx = valid.map { it.mmPerPx }.average(),
            fingerWidthMm = median,
            ringSizeSuggestion = ringSize,
            confidence = confidence,
            reasonsFail = reasons,
            debugMetrics =
                mapOf(
                    "validFrames" to valid.size,
                    "widthStdDev" to stddev,
                    "medianWidthMm" to median,
                ),
        )
    }

    private fun estimateRingSize(widthMm: Double): String {
        val circumferenceCm = (widthMm * Math.PI) / 10.0
        val table =
            listOf(
                4.7 to 15.0,
                5.1 to 16.0,
                5.4 to 17.0,
                5.7 to 18.0,
                6.1 to 19.0,
                6.4 to 20.0,
            )
        val closest = table.minByOrNull { kotlin.math.abs(it.first - circumferenceCm) } ?: return "N/A"
        return "VN ${closest.first} (diameter ${closest.second}mm)"
    }
}

data class FrameMeasurement(
    val timestampMs: Long,
    val mmPerPx: Double,
    val widthMm: Double,
    val cardConfidence: Float,
    val handConfidence: Float,
    val qualityScore: Float,
)

