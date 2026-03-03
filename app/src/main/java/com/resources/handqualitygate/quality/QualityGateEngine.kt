package com.resources.handqualitygate.quality

import android.util.Size
import com.resources.handqualitygate.tracking.HandObservation
import java.util.ArrayDeque

data class QualityResult(
    val timestampMs: Long,
    val Q_total: Float,
    val q_blur: Float,
    val q_motion: Float,
    val q_exposure: Float,
    val q_roi: Float,
    val q_conf: Float,
    val reasonsFail: List<String>,

    // Raw metrics (current frame).
    val blurVoL: Double,
    val motionMad: Double,
    val meanY: Double,
    val stdY: Double,
    val pctHigh: Double,
    val pctLow: Double,
    val roiScore: Float,
    val confidence: Float,
)

class QualityGateEngine(private val config: QualityGateConfig) {
    private var prevRoi: ByteArray? = null
    private val window = ArrayDeque<QualitySample>(config.aggregationWindow)
    private var sumTotal = 0f
    private var sumBlur = 0f
    private var sumMotion = 0f
    private var sumExposure = 0f
    private var sumRoi = 0f
    private var sumConf = 0f

    fun evaluate(
        timestampMs: Long,
        lumaRoi: ByteArray,
        roiRectPx: android.graphics.Rect,
        frameSize: Size,
        observation: HandObservation,
    ): QualityResult {
        val reasons = mutableListOf<QualityFailReason>()

        val hasHand = observation.hasHand
        val qConf = if (hasHand) observation.confidence.coerceIn(0f, 1f) else 0f
        if (!hasHand) {
            reasons += QualityFailReason.NO_HAND
        } else if (qConf < 0.5f) {
            reasons += QualityFailReason.LOW_CONF
        }

        val roiScore = QualityMetrics.computeRoiScore(roiRectPx, frameSize)
        val qRoi = roiScore.coerceIn(0f, 1f)
        if (qRoi < 0.5f) reasons += QualityFailReason.ROI_BAD

        val blurVoL = QualityMetrics.computeBlurVoL(lumaRoi, config.downsampleSize, config.downsampleSize)
        val qBlur = QualityMetrics.normalizeBlur(blurVoL, config.blurLow, config.blurOk)
        if (qBlur < 0.5f) reasons += QualityFailReason.BLUR_LOW

        val prev = prevRoi
        val motionMad =
            if (prev != null) {
                QualityMetrics.computeMotionMAD(lumaRoi, prev)
            } else {
                0.0
            }
        val qMotion = QualityMetrics.normalizeMotion(motionMad, config.motionLow, config.motionHigh)
        if (motionMad > config.motionHigh) reasons += QualityFailReason.MOTION_HIGH

        val exp = QualityMetrics.computeExposureStats(lumaRoi)
        val qExposure =
            QualityMetrics.normalizeExposure(
                stats = exp,
                minMean = config.exposureMinMean,
                maxMean = config.exposureMaxMean,
                minStd = config.exposureMinStd,
                pctClipMax = config.exposurePctClipMax,
            )
        if (exp.pctHigh > config.exposurePctClipMax) reasons += QualityFailReason.EXPOSURE_CLIP_HIGH
        if (exp.pctLow > config.exposurePctClipMax) reasons += QualityFailReason.EXPOSURE_CLIP_LOW
        if (exp.mean < config.exposureMinMean || exp.mean > config.exposureMaxMean) reasons += QualityFailReason.EXPOSURE_MEAN_OUT
        if (exp.std < config.exposureMinStd) reasons += QualityFailReason.EXPOSURE_LOW_CONTRAST

        val totalRaw =
            (config.wBlur * qBlur) +
                (config.wMotion * qMotion) +
                (config.wExposure * qExposure) +
                (config.wRoi * qRoi) +
                (config.wConf * qConf)

        val sample = QualitySample(totalRaw, qBlur, qMotion, qExposure, qRoi, qConf)
        window.addLast(sample)
        sumTotal += sample.total
        sumBlur += sample.qBlur
        sumMotion += sample.qMotion
        sumExposure += sample.qExposure
        sumRoi += sample.qRoi
        sumConf += sample.qConf
        while (window.size > config.aggregationWindow) {
            val removed = window.removeFirst()
            sumTotal -= removed.total
            sumBlur -= removed.qBlur
            sumMotion -= removed.qMotion
            sumExposure -= removed.qExposure
            sumRoi -= removed.qRoi
            sumConf -= removed.qConf
        }
        prevRoi = lumaRoi

        val avg = average()

        return QualityResult(
            timestampMs = timestampMs,
            Q_total = avg.total,
            q_blur = avg.qBlur,
            q_motion = avg.qMotion,
            q_exposure = avg.qExposure,
            q_roi = avg.qRoi,
            q_conf = avg.qConf,
            reasonsFail = reasons.asReasonStrings(),
            blurVoL = blurVoL,
            motionMad = motionMad,
            meanY = exp.mean,
            stdY = exp.std,
            pctHigh = exp.pctHigh,
            pctLow = exp.pctLow,
            roiScore = roiScore,
            confidence = qConf,
        )
    }

    private data class QualitySample(
        val total: Float,
        val qBlur: Float,
        val qMotion: Float,
        val qExposure: Float,
        val qRoi: Float,
        val qConf: Float,
    )

    private data class QualityAvg(
        val total: Float,
        val qBlur: Float,
        val qMotion: Float,
        val qExposure: Float,
        val qRoi: Float,
        val qConf: Float,
    )

    private fun average(): QualityAvg {
        val count = window.size
        if (count == 0) return QualityAvg(0f, 0f, 0f, 0f, 0f, 0f)
        val n = count.toFloat()
        return QualityAvg(sumTotal / n, sumBlur / n, sumMotion / n, sumExposure / n, sumRoi / n, sumConf / n)
    }
}
