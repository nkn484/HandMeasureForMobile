package com.resources.handmeasure.sdk.internal.ui

import android.app.Application
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.AndroidViewModel
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureCallbacks
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureState
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureStateMachine
import com.resources.handmeasure.sdk.internal.autocapture.CaptureResult
import com.resources.handmeasure.sdk.internal.autocapture.CapturedFrame
import com.resources.handmeasure.sdk.internal.camera.HandQualityAnalyzer
import com.resources.handmeasure.sdk.internal.logging.CsvMetricsLogger
import com.resources.handmeasure.sdk.internal.quality.QualityGateConfig
import com.resources.handmeasure.sdk.internal.quality.QualityGateEngine
import com.resources.handmeasure.sdk.internal.ringsize.FramePacket
import com.resources.handmeasure.sdk.internal.ringsize.MediaPipeHandLandmarkerEngine
import com.resources.handmeasure.sdk.internal.ringsize.RingSizeEstimator
import com.resources.handmeasure.sdk.internal.ringsize.ScaleEstimator
import com.resources.handmeasure.sdk.internal.tracking.MediaPipeHandTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream

class MainViewModel(
    application: Application,
    private val requireReference: Boolean = true,
    private val debugEnabledDefault: Boolean = false,
    private val referenceWidthMm: Double = 85.60,
    private val referenceHeightMm: Double = 53.98,
) : AndroidViewModel(application), AutoCaptureCallbacks {
    private val config =
        QualityGateConfig(
            requireCardForCapture = requireReference,
        )
    private val handLandmarkerEngine = MediaPipeHandLandmarkerEngine(application)
    private val tracker = MediaPipeHandTracker(handLandmarkerEngine)
    private val engine = QualityGateEngine(config)
    private val ringSizeEstimator =
        RingSizeEstimator(
            scaleEstimator = ScaleEstimator(referenceWidthMm, referenceHeightMm),
            handEngine = handLandmarkerEngine,
        )

    private val capturesRoot = File(application.filesDir, "captures").apply { mkdirs() }
    private val csvLogger = CsvMetricsLogger(application, config.enableCsvLogging)

    private val stateMachine = AutoCaptureStateMachine(config, this)

    val analyzer: ImageAnalysis.Analyzer =
        HandQualityAnalyzer(
            config = config,
            tracker = tracker,
            engine = engine,
            stateMachine = stateMachine,
            csvLogger = if (config.enableCsvLogging) csvLogger else null,
        ) { quality ->
            _uiState.update { it.copy(metrics = quality) }
        }

    private val _uiState = MutableStateFlow(QualityUiState(debugEnabled = debugEnabledDefault))
    val uiState: StateFlow<QualityUiState> = _uiState.asStateFlow()

    fun setDebugEnabled(enabled: Boolean) {
        _uiState.update { it.copy(debugEnabled = enabled) }
    }

    override fun onStateChanged(state: AutoCaptureState, progress: Float) {
        _uiState.update {
            it.copy(
                state = state,
                progress = progress,
                hintText = hintFor(state),
            )
        }
    }

    override fun onCaptureCompleted(result: CaptureResult) {
        val topFrames = result.topFrames
        val packets =
            topFrames.map {
                FramePacket(
                    timestampMs = it.timestampMs,
                    qualityScore = it.score,
                    jpegBytes = it.jpegBytes,
                )
            }

        val sizeResult = ringSizeEstimator.estimateSize(packets)
        val debugEnabled = _uiState.value.debugEnabled
        val savedPaths = if (debugEnabled) saveFrames(result.sessionId, topFrames) else emptyList()

        _uiState.update {
            it.copy(
                savedPaths = savedPaths,
                sizeResult = sizeResult,
                resultVersion = System.currentTimeMillis(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        csvLogger.close()
        try {
            handLandmarkerEngine.close()
        } catch (_: Exception) {
            // Ignore.
        }
    }

    private fun hintFor(state: AutoCaptureState): String =
        when (state) {
            AutoCaptureState.SEARCH -> "Ðýa tay và th? vào khung"
            AutoCaptureState.READY -> "Gi? tay ?n ð?nh"
            AutoCaptureState.STABLE -> "Ðang ?n ð?nh... chu?n b? ch?p"
            AutoCaptureState.CAPTURE -> "Ðang ch?p..."
            AutoCaptureState.COOLDOWN -> "Ðang ngh?..."
        }

    private fun saveFrames(sessionId: Long, frames: List<CapturedFrame>): List<String> {
        val sessionDir = File(capturesRoot, "session_$sessionId").apply { mkdirs() }
        val paths = ArrayList<String>(frames.size)
        for ((index, frame) in frames.withIndex()) {
            val safeScore = (frame.score * 1000).toInt()
            val file = File(sessionDir, "frame_${index + 1}_${frame.timestampMs}_q${safeScore}.jpg")
            FileOutputStream(file).use { it.write(frame.jpegBytes) }
            paths.add(file.absolutePath)
        }
        return paths
    }
}
