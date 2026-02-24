package com.resources.handmeasure.sdk

import android.graphics.Rect
import android.graphics.RectF
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureCallbacks
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureState
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureStateMachine
import com.resources.handmeasure.sdk.internal.autocapture.CaptureResult
import com.resources.handmeasure.sdk.internal.autocapture.CapturedFrame
import com.resources.handmeasure.sdk.internal.quality.QualityGateConfig
import com.resources.handmeasure.sdk.internal.quality.QualityResult
import com.resources.handmeasure.sdk.internal.tracking.HandObservation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class DummyCallbacks : AutoCaptureCallbacks {
    var lastState: AutoCaptureState = AutoCaptureState.SEARCH
    var captured: CaptureResult? = null
    override fun onStateChanged(state: AutoCaptureState, progress: Float) { lastState = state }
    override fun onCaptureCompleted(result: CaptureResult) { captured = result }
}

@RunWith(RobolectricTestRunner::class)
class AutoCaptureStateMachineTest {
    @Test
    fun `transitions to CAPTURE when stable quality`() {
        val callbacks = DummyCallbacks()
        val sm = AutoCaptureStateMachine(QualityGateConfig(stableFrames = 2), callbacks)
        val obs = HandObservation(roiNormalized = RectF(), roiPixel = Rect(0, 0, 10, 10), confidence = 1f, hasHand = true)
        val q = QualityResult(0, Q_total = 1f, q_blur = 1f, q_motion = 1f, q_exposure = 1f, q_roi = 1f, q_conf = 1f, reasonsFail = emptyList(), blurVoL = 0.0, motionMad = 0.0, meanY = 100.0, stdY = 10.0, pctHigh = 0.0, pctLow = 0.0, roiScore = 1f, confidence = 1f)

        sm.update(0, obs, q)
        sm.update(50, obs, q)
        sm.update(100, obs, q)

        assertEquals(AutoCaptureState.CAPTURE, callbacks.lastState)
    }
}
