package com.resources.handqualitygate.ui

import android.app.Application
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.AndroidViewModel
import com.resources.handqualitygate.autocapture.CaptureResult
import com.resources.handqualitygate.autocapture.AutoCaptureCallbacks
import com.resources.handqualitygate.autocapture.AutoCaptureState
import com.resources.handqualitygate.autocapture.AutoCaptureStateMachine
import com.resources.handqualitygate.autocapture.CapturedFrame
import com.resources.handqualitygate.camera.HandQualityAnalyzer
import com.resources.handqualitygate.logging.CsvMetricsLogger
import com.resources.handqualitygate.quality.QualityGateConfig
import com.resources.handqualitygate.quality.QualityGateEngine
import com.resources.handqualitygate.ringsize.FramePacket
import com.resources.handqualitygate.ringsize.MediaPipeHandLandmarkerEngine
import com.resources.handqualitygate.ringsize.RingSizeEstimator
import com.resources.handqualitygate.tracking.MediaPipeHandTracker
import com.resources.handqualitygate.upload.CaptureUploader
import com.resources.handqualitygate.upload.NoopCaptureUploader
import com.resources.handqualitygate.upload.OkHttpCaptureUploader
import com.resources.handqualitygate.upload.UploadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainViewModel(application: Application) : AndroidViewModel(application), AutoCaptureCallbacks {
    private val config = QualityGateConfig()
    private val handLandmarkerEngine = MediaPipeHandLandmarkerEngine(application)
    private val tracker = MediaPipeHandTracker(handLandmarkerEngine)
    private val engine = QualityGateEngine(config)
    private val ringSizeEstimator = RingSizeEstimator(handEngine = handLandmarkerEngine)
    private val uploadConfig =
        UploadConfig(
            enabled = true,
            endpointUrl = "",
            apiKey = "",
        )
    private val uploader: CaptureUploader =
        if (uploadConfig.endpointUrl.isBlank()) {
            NoopCaptureUploader()
        } else {
            OkHttpCaptureUploader(uploadConfig)
        }
    private val uploadExecutor = Executors.newSingleThreadExecutor()

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

    private val _uiState = MutableStateFlow(QualityUiState())
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

        if (debugEnabled) {
            uploadExecutor.execute {
                try {
                    uploader.upload(result.sessionId, topFrames)
                } catch (_: Throwable) {
                    // Swallow upload failures for now; hook in logging if needed.
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        csvLogger.close()
        uploadExecutor.shutdown()
        try {
            handLandmarkerEngine.close()
        } catch (_: Exception) {
            // Ignore.
        }
    }

    private fun hintFor(state: AutoCaptureState): String =
        when (state) {
            AutoCaptureState.SEARCH -> "Xin hãy đưa tay vào khung hình"
            AutoCaptureState.READY -> "Hãy giữ tay ổn định"
            AutoCaptureState.STABLE -> "Đã ổn định...chuẩn bị chụp"
            AutoCaptureState.CAPTURE -> "Đang chụp..."
            AutoCaptureState.COOLDOWN -> "Dang nghi..."
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
