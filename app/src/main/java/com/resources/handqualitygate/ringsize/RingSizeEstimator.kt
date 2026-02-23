package com.resources.handqualitygate.ringsize

import java.io.File

class RingSizeEstimator(
    private val cardDetector: CardDetector = OpenCvCardDetector(),
    private val scaleEstimator: ScaleEstimator = ScaleEstimator(),
    private val handEngine: HandLandmarkerEngine = FakeHandLandmarkerEngine(),
    private val fingerMeasurer: FingerWidthMeasurer = FingerWidthMeasurer(),
    private val aggregator: SizeAggregator = SizeAggregator(),
) {
    fun estimateSize(capturedFrames: List<FramePacket>): SizeResult {
        val measurements = mutableListOf<FrameMeasurement>()
        val debugReasons = mutableListOf<String>()

        for (frame in capturedFrames) {
            val card = cardDetector.detect(frame)
            if (card == null) {
                debugReasons += "CARD_NOT_FOUND"
                continue
            }
            val scale = scaleEstimator.estimate(card)
            if (scale == null) {
                debugReasons += "SCALE_FAIL"
                continue
            }
            val hand = handEngine.detect(frame)
            if (hand == null) {
                debugReasons += "HAND_NOT_FOUND"
                continue
            }
            val widthResult = fingerMeasurer.measure(frame, hand, scale.mmPerPx)
            if (widthResult == null) {
                debugReasons += "WIDTH_FAIL"
                continue
            }

            measurements.add(
                FrameMeasurement(
                    timestampMs = frame.timestampMs,
                    mmPerPx = scale.mmPerPx,
                    widthMm = widthResult.widthMm,
                    cardConfidence = card.confidence,
                    handConfidence = hand.confidence,
                    qualityScore = frame.qualityScore,
                )
            )
        }

        val result = aggregator.aggregate(measurements)
        if (result.reasonsFail.isEmpty() && debugReasons.isNotEmpty()) {
            return result.copy(reasonsFail = debugReasons.distinct())
        }
        return result
    }

    companion object {
        fun fromCaptureFiles(paths: List<String>, defaultQuality: Float = 1.0f): List<FramePacket> {
            return paths.map { path ->
                val bytes = File(path).readBytes()
                FramePacket(timestampMs = System.currentTimeMillis(), qualityScore = defaultQuality, jpegBytes = bytes)
            }
        }
    }
}
