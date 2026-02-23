package com.resources.handqualitygate.autocapture

import android.graphics.Rect
import android.graphics.RectF
import com.resources.handqualitygate.quality.QualityGateConfig
import com.resources.handqualitygate.quality.QualityResult
import com.resources.handqualitygate.tracking.HandObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoCaptureStateMachineTest {
    @Test
    fun searchReadyStableCaptureCooldownFlow_emitsExpectedTransitionsAndTopK() {
        val config =
            QualityGateConfig(
                readyThreshold = 0.6f,
                stableThreshold = 0.7f,
                stableFrames = 2,
                captureDurationMs = 100L,
                cooldownMs = 200L,
                topK = 2,
                jitterThresholdPx = 100f,
                requireCardForCapture = false,
            )
        val callbacks = RecordingCallbacks()
        val stateMachine = AutoCaptureStateMachine(config, callbacks)
        val observation = observation()

        assertEquals(AutoCaptureState.SEARCH, stateMachine.state)

        assertFalse(stateMachine.update(1_000L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.READY, stateMachine.state)

        assertFalse(stateMachine.update(1_040L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.STABLE, stateMachine.state)

        assertTrue(stateMachine.update(1_080L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.CAPTURE, stateMachine.state)

        stateMachine.addCapturedFrame(CapturedFrame(1_081L, 0.30f, byteArrayOf(1)))
        stateMachine.addCapturedFrame(CapturedFrame(1_082L, 0.90f, byteArrayOf(2)))
        stateMachine.addCapturedFrame(CapturedFrame(1_083L, 0.60f, byteArrayOf(3)))

        assertFalse(stateMachine.update(1_200L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.COOLDOWN, stateMachine.state)
        assertNotNull(callbacks.captureResult)
        assertEquals(2, callbacks.captureResult!!.topFrames.size)
        assertEquals(0.90f, callbacks.captureResult!!.topFrames[0].score, 0.0001f)
        assertEquals(0.60f, callbacks.captureResult!!.topFrames[1].score, 0.0001f)

        assertFalse(stateMachine.update(1_250L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.COOLDOWN, stateMachine.state)

        assertFalse(stateMachine.update(1_450L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.SEARCH, stateMachine.state)
    }

    @Test
    fun readyFallsBackToSearch_whenReadyConditionBreaks() {
        val config =
            QualityGateConfig(
                readyThreshold = 0.6f,
                stableThreshold = 0.7f,
                requireCardForCapture = false,
            )
        val callbacks = RecordingCallbacks()
        val stateMachine = AutoCaptureStateMachine(config, callbacks)
        val observation = observation()

        assertFalse(stateMachine.update(1_000L, observation, quality(total = 0.8f)))
        assertEquals(AutoCaptureState.READY, stateMachine.state)

        assertFalse(
            stateMachine.update(
                1_040L,
                observation,
                quality(total = 0.2f, reasons = listOf("LOW_QUALITY")),
            )
        )
        assertEquals(AutoCaptureState.SEARCH, stateMachine.state)
    }

    private class RecordingCallbacks : AutoCaptureCallbacks {
        var captureResult: CaptureResult? = null

        override fun onStateChanged(state: AutoCaptureState, progress: Float) = Unit

        override fun onCaptureCompleted(result: CaptureResult) {
            captureResult = result
        }
    }

    private fun observation(): HandObservation {
        val rect = Rect(100, 100, 240, 300)
        return HandObservation(
            roiNormalized = RectF(0.2f, 0.2f, 0.5f, 0.6f),
            roiPixel = rect,
            confidence = 1f,
            hasHand = true,
        )
    }

    private fun quality(total: Float, reasons: List<String> = emptyList()): QualityResult {
        return QualityResult(
            timestampMs = 0L,
            Q_total = total,
            q_blur = total,
            q_motion = total,
            q_exposure = total,
            q_roi = total,
            q_conf = total,
            reasonsFail = reasons,
            blurVoL = 120.0,
            motionMad = 1.0,
            meanY = 128.0,
            stdY = 24.0,
            pctHigh = 0.01,
            pctLow = 0.01,
            roiScore = 0.9f,
            confidence = 1.0f,
        )
    }
}
