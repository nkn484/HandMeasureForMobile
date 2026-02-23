package com.resources.handqualitygate.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.resources.handqualitygate.autocapture.AutoCaptureState
import com.resources.handqualitygate.autocapture.AutoCaptureStateMachine
import com.resources.handqualitygate.autocapture.CapturedFrame
import com.resources.handqualitygate.logging.CsvMetricsLogger
import com.resources.handqualitygate.quality.LumaRoiExtractor
import com.resources.handqualitygate.quality.QualityGateConfig
import com.resources.handqualitygate.quality.QualityGateEngine
import com.resources.handqualitygate.quality.QualityResult
import com.resources.handqualitygate.ringsize.CardDetection
import com.resources.handqualitygate.ringsize.CardDetector
import com.resources.handqualitygate.ringsize.FramePacket
import com.resources.handqualitygate.ringsize.OpenCvCardDetector
import com.resources.handqualitygate.tracking.HandTracker
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
                CapturedFrame(timestampMs = tsMs, score = gatedQuality.Q_total, jpegBytes = jpeg)
            )
        }

        csvLogger?.log(tsMs, stateMachine.state, gatedQuality)
        onMetrics(gatedQuality)

        image.close()
    }

    private fun applyCardGate(
        nowMs: Long,
        timestampMs: Long,
        image: ImageProxy,
        quality: QualityResult,
    ): QualityResult {
        if (!config.requireCardForCapture) return quality
        if (stateMachine.state == AutoCaptureState.COOLDOWN) return quality

        val needRefresh =
            lastCardDetection == null || nowMs - lastCardAnalyzeAtMs >= config.cardAnalysisIntervalMs
        if (needRefresh) {
            lastCardAnalyzeAtMs = nowMs
            val frame = FramePacket(timestampMs = timestampMs, qualityScore = quality.Q_total, imageProxy = image)
            lastCardDetection = cardDetector.detect(frame)
        }

        val card = lastCardDetection
        val reasons = quality.reasonsFail.toMutableList()
        if (card == null) {
            reasons += "CARD_NOT_FOUND"
        } else if (card.confidence < config.cardMinConfidence) {
            reasons += "CARD_LOW_CONF"
        }

        if (reasons.size == quality.reasonsFail.size) return quality
        return quality.copy(reasonsFail = reasons.distinct())
    }
}
