package com.resources.handmeasure.sdk.internal.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureState
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureStateMachine
import com.resources.handmeasure.sdk.internal.autocapture.CapturedFrame
import com.resources.handmeasure.sdk.internal.logging.CsvMetricsLogger
import com.resources.handmeasure.sdk.internal.quality.LumaRoiExtractor
import com.resources.handmeasure.sdk.internal.quality.QualityGateConfig
import com.resources.handmeasure.sdk.internal.quality.QualityGateEngine
import com.resources.handmeasure.sdk.internal.quality.QualityResult
import com.resources.handmeasure.sdk.internal.quality.QualityFailReason
import com.resources.handmeasure.sdk.internal.ringsize.CardDetection
import com.resources.handmeasure.sdk.internal.ringsize.CardDetector
import com.resources.handmeasure.sdk.internal.ringsize.FramePacket
import com.resources.handmeasure.sdk.internal.ringsize.OpenCvCardDetector
import com.resources.handmeasure.sdk.internal.tracking.HandTracker
import java.util.concurrent.TimeUnit

class HandQualityAnalyzer(
    private val config: QualityGateConfig,
    private val tracker: HandTracker,
    private val engine: QualityGateEngine,
    private val stateMachine: AutoCaptureStateMachine,
    private val csvLogger: CsvMetricsLogger?,
    private val cardDetector: CardDetector = OpenCvCardDetector(),
    private val onMetrics: (QualityResult) -> Unit,
) : ImageAnalysis.Analyzer {
    private var lastAnalyzeAtMs: Long = 0L
    private var lastCardAnalyzeAtMs: Long = 0L
    private var lastCardDetection: CardDetection? = null

    override fun analyze(image: ImageProxy) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastAnalyzeAtMs < config.analysisMinIntervalMs) {
            image.close()
            return
        }
        lastAnalyzeAtMs = nowMs

        try {
            val tsMs = TimeUnit.NANOSECONDS.toMillis(image.imageInfo.timestamp)

            val observation = tracker.observe(image)
            val lumaRoi =
                LumaRoiExtractor.downsampleToSquare(
                    image = image,
                    roiPx = observation.roiPixel,
                    outSize = config.downsampleSize,
                )

            val quality =
                engine.evaluate(
                    timestampMs = tsMs,
                    lumaRoi = lumaRoi,
                    roiRectPx = observation.roiPixel,
                    frameSize = android.util.Size(image.width, image.height),
                    observation = observation,
                )

            val gatedQuality = applyCardGate(nowMs, tsMs, image, quality)
            val shouldCapture = stateMachine.update(tsMs, observation, gatedQuality)
            if (shouldCapture) {
                val jpeg = ImageUtils.imageProxyToJpeg(image, quality = 90)
                stateMachine.addCapturedFrame(
                    CapturedFrame(timestampMs = tsMs, score = gatedQuality.Q_total, jpegBytes = jpeg),
                )
            }

            csvLogger?.log(tsMs, stateMachine.state, gatedQuality)
            onMetrics(gatedQuality)
        } catch (t: Throwable) {
            Log.w("HandQualityAnalyzer", "Analyze failed: ${t.message}")
        } finally {
            image.close()
        }
    }

    private fun applyCardGate(
        nowMs: Long,
        timestampMs: Long,
        image: ImageProxy,
        quality: QualityResult,
    ): QualityResult {
        if (!config.requireCardForCapture) return quality
        if (stateMachine.state == AutoCaptureState.COOLDOWN) return quality

        // Save CPU: don't run the expensive OpenCV card detection until a hand is reliably present.
        if (quality.reasonsFail.contains(QualityFailReason.NO_HAND.name)) {
            return quality
        }

        val needRefresh =
            lastCardDetection == null || nowMs - lastCardAnalyzeAtMs >= config.cardAnalysisIntervalMs
        if (needRefresh) {
            lastCardAnalyzeAtMs = nowMs
            val frame = FramePacket(timestampMs = timestampMs, qualityScore = quality.Q_total, imageProxy = image)
            lastCardDetection =
                try {
                    cardDetector.detect(frame)
                } catch (t: Throwable) {
                    Log.w("HandQualityAnalyzer", "Card detection failed: ${t.message}")
                    null
                }
        }

        val card = lastCardDetection
        val reasons = quality.reasonsFail.toMutableList()
        if (card == null) {
            reasons += QualityFailReason.CARD_NOT_FOUND.name
        } else if (card.confidence < config.cardMinConfidence) {
            reasons += QualityFailReason.CARD_LOW_CONF.name
        }

        val nextCardConfidence = card?.confidence
        val nextReasons = reasons.distinct()

        if (nextReasons == quality.reasonsFail && nextCardConfidence == quality.cardConfidence) return quality
        return quality.copy(reasonsFail = nextReasons, cardConfidence = nextCardConfidence)
    }
}
