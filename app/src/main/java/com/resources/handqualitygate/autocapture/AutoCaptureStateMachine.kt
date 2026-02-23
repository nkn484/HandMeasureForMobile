package com.resources.handqualitygate.autocapture

import android.graphics.PointF
import com.resources.handqualitygate.quality.QualityGateConfig
import com.resources.handqualitygate.quality.QualityResult
import com.resources.handqualitygate.tracking.HandObservation
import kotlin.math.hypot

enum class AutoCaptureState {
    SEARCH,
    READY,
    STABLE,
    CAPTURE,
    COOLDOWN,
}

interface AutoCaptureCallbacks {
    fun onStateChanged(state: AutoCaptureState, progress: Float)
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

    fun update(timestampMs: Long, observation: HandObservation, quality: QualityResult): Boolean {
        val roiCenter = PointF(observation.roiPixel.exactCenterX(), observation.roiPixel.exactCenterY())

        if (state == AutoCaptureState.COOLDOWN) {
            if (timestampMs >= cooldownUntilMs) {
                transition(AutoCaptureState.SEARCH, 0f)
            } else {
                val remaining = (cooldownUntilMs - timestampMs).coerceAtLeast(0L)
                val progress = 1f - (remaining.toFloat() / config.cooldownMs.toFloat()).coerceIn(0f, 1f)
                callbacks.onStateChanged(state, progress)
            }
            return false
        }

        val hasHand = observation.hasHand
        val ready = hasHand && quality.Q_total >= config.readyThreshold && quality.reasonsFail.isEmpty()
        val stable = hasHand && quality.Q_total >= config.stableThreshold && quality.reasonsFail.isEmpty()

        val jitterOk = isJitterOk(roiCenter)
        lastRoiCenter = roiCenter

        when (state) {
            AutoCaptureState.SEARCH -> {
                if (ready) transition(AutoCaptureState.READY, 0f)
            }
            AutoCaptureState.READY -> {
                if (!ready) {
                    stableCount = 0
                    transition(AutoCaptureState.SEARCH, 0f)
                } else if (stable && jitterOk) {
                    stableCount = 1
                    transition(AutoCaptureState.STABLE, stableCount.toFloat() / config.stableFrames.toFloat())
                } else {
                    callbacks.onStateChanged(state, 0f)
                }
            }
            AutoCaptureState.STABLE -> {
                if (!stable || !jitterOk) {
                    stableCount = 0
                    transition(AutoCaptureState.READY, 0f)
                } else {
                    stableCount++
                    val progress = (stableCount.toFloat() / config.stableFrames.toFloat()).coerceIn(0f, 1f)
                    callbacks.onStateChanged(state, progress)
                    if (stableCount >= config.stableFrames) {
                        startCapture(timestampMs)
                    }
                }
            }
            AutoCaptureState.CAPTURE -> {
                val elapsed = timestampMs - captureStartMs
                val progress = (elapsed.toFloat() / config.captureDurationMs.toFloat()).coerceIn(0f, 1f)
                callbacks.onStateChanged(state, progress)
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

    private fun startCapture(timestampMs: Long) {
        capturedFrames.clear()
        captureStartMs = timestampMs
        stableCount = 0
        transition(AutoCaptureState.CAPTURE, 0f)
    }

    private fun finishCapture(timestampMs: Long) {
        val sessionId = captureStartMs
        val selected = capturedFrames.sortedByDescending { it.score }.take(config.topK)
        capturedFrames.clear()
        callbacks.onCaptureCompleted(CaptureResult(sessionId = sessionId, topFrames = selected))
        cooldownUntilMs = timestampMs + config.cooldownMs
        transition(AutoCaptureState.COOLDOWN, 0f)
    }

    private fun transition(newState: AutoCaptureState, progress: Float) {
        state = newState
        callbacks.onStateChanged(state, progress)
    }

    private fun isJitterOk(currCenter: PointF): Boolean {
        val prev = lastRoiCenter ?: return true
        val d = hypot((currCenter.x - prev.x).toDouble(), (currCenter.y - prev.y).toDouble())
        return d <= config.jitterThresholdPx
    }
}
