package com.resources.handmeasure.sdk.internal.autocapture

import android.graphics.PointF
import com.resources.handmeasure.sdk.internal.quality.QualityGateConfig
import com.resources.handmeasure.sdk.internal.quality.QualityFailReason
import com.resources.handmeasure.sdk.internal.quality.QualityResult
import com.resources.handmeasure.sdk.internal.tracking.HandObservation
import kotlin.math.hypot

enum class AutoCaptureState {
    SEARCH,
    READY,
    STABLE,
    CAPTURE,
    COOLDOWN,
}

interface AutoCaptureCallbacks {
    fun onStateChanged(state: AutoCaptureState, progress: Float, holdProgress: Float)
    fun onCaptureCompleted(result: CaptureResult)
}

data class CapturedFrame(
    val timestampMs: Long,
    val score: Float,
    val jpegBytes: ByteArray,
)

data class CaptureResult(
    val sessionId: Long,
    val topFrames: List<CapturedFrame>,
)

class AutoCaptureStateMachine(
    private val config: QualityGateConfig,
    private val callbacks: AutoCaptureCallbacks,
) {
    var state: AutoCaptureState = AutoCaptureState.SEARCH
        private set

    private var stableCount = 0
    private var captureStartMs = 0L
    private var cooldownUntilMs = 0L
    private var lastRoiCenter: PointF? = null
    private val capturedFrames = ArrayList<CapturedFrame>(64)
    private var bothDetectedSinceMs = 0L

    fun update(timestampMs: Long, observation: HandObservation, quality: QualityResult): Boolean {
        val hasHand = observation.hasHand
        val roiCenter =
            if (hasHand) {
                PointF(observation.roiPixel.exactCenterX(), observation.roiPixel.exactCenterY())
            } else {
                null
            }
        if (!hasHand) {
            // Reset jitter baseline while hand is missing to avoid false jitter spikes on re-acquisition.
            lastRoiCenter = null
        }

        val jitterOk = roiCenter?.let { isJitterOk(it) } ?: true
        if (roiCenter != null) {
            lastRoiCenter = roiCenter
        }

        val reasons = quality.reasonsFail
        val handOk =
            hasHand &&
                !reasons.contains(QualityFailReason.NO_HAND.name) &&
                !reasons.contains(QualityFailReason.LOW_CONF.name)
        val cardOk =
            !config.requireCardForCapture ||
                (!reasons.contains(QualityFailReason.CARD_NOT_FOUND.name) &&
                    !reasons.contains(QualityFailReason.CARD_LOW_CONF.name))

        val bothOk = handOk && cardOk
        if (bothOk) {
            if (bothDetectedSinceMs <= 0L) bothDetectedSinceMs = timestampMs
        } else {
            bothDetectedSinceMs = 0L
        }

        val captureQualityOk =
            quality.Q_total >= config.readyThreshold &&
                !reasons.contains(QualityFailReason.ROI_BAD.name) &&
                !reasons.contains(QualityFailReason.MOTION_HIGH.name) &&
                !reasons.contains(QualityFailReason.BLUR_LOW.name) &&
                !reasons.contains(QualityFailReason.EXPOSURE_CLIP_HIGH.name) &&
                !reasons.contains(QualityFailReason.EXPOSURE_CLIP_LOW.name) &&
                !reasons.contains(QualityFailReason.EXPOSURE_MEAN_OUT.name) &&
                !reasons.contains(QualityFailReason.EXPOSURE_LOW_CONTRAST.name)

        val holdEnabled = config.requireCardForCapture && config.bothDetectedHoldMs > 0L
        val holdProgress =
            if (holdEnabled && bothOk && bothDetectedSinceMs > 0L) {
                ((timestampMs - bothDetectedSinceMs).toFloat() / config.bothDetectedHoldMs.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
        val holdComplete = holdEnabled && holdProgress >= 1f

        if (state == AutoCaptureState.COOLDOWN) {
            if (timestampMs >= cooldownUntilMs) {
                transition(AutoCaptureState.SEARCH, 0f, 0f)
            } else {
                val remaining = (cooldownUntilMs - timestampMs).coerceAtLeast(0L)
                val progress = 1f - (remaining.toFloat() / config.cooldownMs.toFloat()).coerceIn(0f, 1f)
                callbacks.onStateChanged(state, progress, 0f)
            }
            return false
        }

        // Fast-path: once we can reliably see both hand + card AND the frame quality is OK, start capture
        // without waiting for many stable frames. This prevents getting stuck in READY/STABLE when
        // thresholds are hard to satisfy in real-world lighting.
        if (bothOk && jitterOk && captureQualityOk && state != AutoCaptureState.CAPTURE) {
            startCapture(timestampMs, if (holdEnabled) holdProgress else 0f)
            return true
        }

        // If we can reliably see both hand + card for a continuous window, proceed to capture.
        if (holdComplete && jitterOk && captureQualityOk && state != AutoCaptureState.CAPTURE) {
            startCapture(timestampMs, 1f)
            return true
        }

        val ready = hasHand && quality.Q_total >= config.readyThreshold && quality.reasonsFail.isEmpty()
        val stable = hasHand && quality.Q_total >= config.stableThreshold && quality.reasonsFail.isEmpty()

        when (state) {
            AutoCaptureState.SEARCH -> {
                if (ready) transition(AutoCaptureState.READY, 0f, holdProgress)
                else callbacks.onStateChanged(state, 0f, holdProgress)
            }
            AutoCaptureState.READY -> {
                if (!ready) {
                    stableCount = 0
                    transition(AutoCaptureState.SEARCH, 0f, holdProgress)
                } else if (stable && jitterOk) {
                    stableCount = 1
                    transition(AutoCaptureState.STABLE, stableCount.toFloat() / config.stableFrames.toFloat(), holdProgress)
                } else {
                    callbacks.onStateChanged(state, 0f, holdProgress)
                }
            }
            AutoCaptureState.STABLE -> {
                if (!stable || !jitterOk) {
                    stableCount = 0
                    transition(AutoCaptureState.READY, 0f, holdProgress)
                } else {
                    stableCount++
                    val progress = (stableCount.toFloat() / config.stableFrames.toFloat()).coerceIn(0f, 1f)
                    callbacks.onStateChanged(state, progress, holdProgress)
                    if (stableCount >= config.stableFrames && (!holdEnabled || holdComplete)) {
                        startCapture(timestampMs, if (holdEnabled) holdProgress else 0f)
                    }
                }
            }
            AutoCaptureState.CAPTURE -> {
                val elapsed = timestampMs - captureStartMs
                val progress = (elapsed.toFloat() / config.captureDurationMs.toFloat()).coerceIn(0f, 1f)
                callbacks.onStateChanged(state, progress, if (holdEnabled) 1f else 0f)
                if (elapsed >= config.captureDurationMs) {
                    finishCapture(timestampMs)
                    return false
                }
                return true
            }
            AutoCaptureState.COOLDOWN -> Unit
        }

        return state == AutoCaptureState.CAPTURE
    }

    fun addCapturedFrame(frame: CapturedFrame) {
        if (state != AutoCaptureState.CAPTURE) return
        capturedFrames.add(frame)
    }

    private fun startCapture(timestampMs: Long, holdProgress: Float) {
        capturedFrames.clear()
        captureStartMs = timestampMs
        stableCount = 0
        transition(AutoCaptureState.CAPTURE, 0f, holdProgress.coerceIn(0f, 1f))
    }

    private fun finishCapture(timestampMs: Long) {
        val sessionId = captureStartMs
        val selected = capturedFrames.sortedByDescending { it.score }.take(config.topK)
        capturedFrames.clear()
        callbacks.onCaptureCompleted(CaptureResult(sessionId = sessionId, topFrames = selected))
        cooldownUntilMs = timestampMs + config.cooldownMs
        transition(AutoCaptureState.COOLDOWN, 0f, 0f)
    }

    private fun transition(newState: AutoCaptureState, progress: Float, holdProgress: Float) {
        state = newState
        callbacks.onStateChanged(state, progress, holdProgress)
    }

    private fun isJitterOk(currCenter: PointF): Boolean {
        val prev = lastRoiCenter ?: return true
        val d = hypot((currCenter.x - prev.x).toDouble(), (currCenter.y - prev.y).toDouble())
        return d <= config.jitterThresholdPx
    }
}
