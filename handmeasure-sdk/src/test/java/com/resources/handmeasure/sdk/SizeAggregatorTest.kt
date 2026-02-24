package com.resources.handmeasure.sdk

import com.resources.handmeasure.sdk.internal.ringsize.FrameMeasurement
import com.resources.handmeasure.sdk.internal.ringsize.SizeAggregator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SizeAggregatorTest {
    @Test
    fun `returns low confidence when not enough frames`() {
        val agg = SizeAggregator(minValidFrames = 3, stableFrames = 4)
        val result = agg.aggregate(emptyList())
        assertTrue(result.confidence < 0.5f)
    }

    @Test
    fun `higher confidence with multiple stable frames`() {
        val agg = SizeAggregator(minValidFrames = 1, stableFrames = 2)
        val frames = listOf(
            FrameMeasurement(0, mmPerPx = 0.5, widthMm = 18.0, cardConfidence = 0.9f, handConfidence = 0.9f, qualityScore = 0.9f),
            FrameMeasurement(1, mmPerPx = 0.5, widthMm = 18.2, cardConfidence = 0.9f, handConfidence = 0.9f, qualityScore = 0.9f),
            FrameMeasurement(2, mmPerPx = 0.5, widthMm = 18.1, cardConfidence = 0.9f, handConfidence = 0.9f, qualityScore = 0.9f),
        )
        val result = agg.aggregate(frames)
        assertTrue(result.confidence > 0.6f)
    }
}
