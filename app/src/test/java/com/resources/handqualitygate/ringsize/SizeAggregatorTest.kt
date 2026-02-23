package com.resources.handqualitygate.ringsize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeAggregatorTest {
    @Test
    fun aggregate_returnsFail_whenValidFramesBelowMinimum() {
        val aggregator = SizeAggregator(minValidFrames = 3)
        val measurements =
            listOf(
                FrameMeasurement(
                    timestampMs = 1_000L,
                    mmPerPx = 0.09,
                    widthMm = 17.4,
                    cardConfidence = 0.6f,
                    handConfidence = 0.9f,
                    qualityScore = 0.8f,
                )
            )

        val result = aggregator.aggregate(measurements)
        assertEquals("N/A", result.ringSizeSuggestion)
        assertTrue(result.reasonsFail.contains("CARD_NOT_FOUND"))
        assertTrue(result.reasonsFail.contains("HAND_NOT_STABLE"))
        assertEquals(0.1f, result.confidence, 0.0001f)
    }

    @Test
    fun aggregate_returnsRingSize_whenEnoughValidFrames() {
        val aggregator = SizeAggregator(minValidFrames = 3, stableFrames = 4)
        val measurements =
            listOf(
                frame(widthMm = 17.7, card = 0.90f, hand = 0.91f),
                frame(widthMm = 17.8, card = 0.92f, hand = 0.95f),
                frame(widthMm = 17.9, card = 0.88f, hand = 0.89f),
                frame(widthMm = 17.8, card = 0.90f, hand = 0.90f),
            )

        val result = aggregator.aggregate(measurements)
        assertNotEquals("N/A", result.ringSizeSuggestion)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.fingerWidthMm > 17.5)
    }

    private fun frame(widthMm: Double, card: Float, hand: Float): FrameMeasurement {
        return FrameMeasurement(
            timestampMs = 1_000L,
            mmPerPx = 0.09,
            widthMm = widthMm,
            cardConfidence = card,
            handConfidence = hand,
            qualityScore = 0.8f,
        )
    }
}
