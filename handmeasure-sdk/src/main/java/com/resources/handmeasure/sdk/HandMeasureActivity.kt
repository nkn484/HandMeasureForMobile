package com.resources.handmeasure.sdk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.resources.handmeasure.sdk.api.CancelReason
import com.resources.handmeasure.sdk.api.HandMeasureError
import com.resources.handmeasure.sdk.api.HandMeasureOutcome
import com.resources.handmeasure.sdk.api.HandMeasureOutcome.Cancelled
import com.resources.handmeasure.sdk.api.HandMeasureOutcome.Failure
import com.resources.handmeasure.sdk.api.HandMeasureOutcome.Success
import com.resources.handmeasure.sdk.api.HandMeasureRequest
import com.resources.handmeasure.sdk.api.MeasurementDebug
import com.resources.handmeasure.sdk.api.MeasurementResult
import com.resources.handmeasure.sdk.api.MeasurementWarning
import com.resources.handmeasure.sdk.api.RingSize
import com.resources.handmeasure.sdk.api.RingSizeRange
import com.resources.handmeasure.sdk.api.RingSizeRecommendation
import com.resources.handmeasure.sdk.api.RingSizeSystem
import com.resources.handmeasure.sdk.internal.autocapture.AutoCaptureState
import com.resources.handmeasure.sdk.internal.camera.CameraController
import com.resources.handmeasure.sdk.internal.quality.QualityFailReason
import com.resources.handmeasure.sdk.internal.ringsize.OpenCvBootstrap
import com.resources.handmeasure.sdk.internal.ringsize.MeasurementFailReason
import com.resources.handmeasure.sdk.internal.ringsize.SizeResult
import com.resources.handmeasure.sdk.internal.ui.MainViewModel
import com.resources.handmeasure.sdk.internal.ui.QualityUiState
import kotlinx.coroutines.delay

class HandMeasureActivity : ComponentActivity() {
    private lateinit var request: HandMeasureRequest

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.AndroidViewModelFactory(application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val cfg = request.config
                val reference = cfg.reference
                val widthMm = when (reference) {
                    is com.resources.handmeasure.sdk.api.ReferenceObject.Custom -> reference.widthMm.toDouble()
                    else -> 85.60
                }
                val heightMm = when (reference) {
                    is com.resources.handmeasure.sdk.api.ReferenceObject.Custom -> reference.heightMm.toDouble()
                    else -> 53.98
                }
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    application = application,
                    requireReference = cfg.requireReference,
                    debugEnabledDefault = cfg.debugEnabled,
                    referenceWidthMm = widthMm,
                    referenceHeightMm = heightMm,
                ) as T
            }
        }
    }

    private var finished = false
    private var userLeft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getParcelableExtra(HandMeasureContract.EXTRA_REQUEST) ?: HandMeasureRequest()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishWith(Cancelled(CancelReason.USER))
                }
            },
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HandMeasureRoot(
                        viewModel = viewModel,
                        request = request,
                        onOutcome = { finishWith(it) },
                        onCancel = { reason -> finishWith(Cancelled(reason)) },
                    )
                }
            }
        }

        val timeoutMs = request.config.timeoutMs
        if (timeoutMs > 0) {
            lifecycleScope.launchWhenStarted {
                delay(timeoutMs)
                if (!finished) finishWith(Cancelled(CancelReason.TIMEOUT))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        userLeft = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeft = true
    }

    override fun onStop() {
        super.onStop()
        if (!finished && userLeft) {
            finishWith(Cancelled(CancelReason.APP_BACKGROUND))
        }
    }

    private fun finishWith(outcome: HandMeasureOutcome) {
        if (finished) return
        finished = true
        val intent = Intent().apply { putExtra(HandMeasureContract.EXTRA_OUTCOME, outcome) }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}

@Composable
private fun HandMeasureRoot(
    viewModel: MainViewModel,
    request: HandMeasureRequest,
    onOutcome: (HandMeasureOutcome) -> Unit,
    onCancel: (CancelReason) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val onOutcomeState = rememberUpdatedState(onOutcome)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.resultVersion) {
        if (uiState.fatalError != null) return@LaunchedEffect
        val result = uiState.sizeResult ?: return@LaunchedEffect
        val outcome = Success(mapResult(result, request))
        onOutcome(outcome)
    }

    LaunchedEffect(uiState.fatalError) {
        val err = uiState.fatalError ?: return@LaunchedEffect
        onOutcome(Failure(err))
    }

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (!granted) onCancel(CancelReason.PERMISSION_DENIED)
        }

    LaunchedEffect(Unit) {
        hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }, onCancel = { onCancel(CancelReason.PERMISSION_DENIED) })
        return
    }

    var openCvOk by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(hasPermission) {
        if (hasPermission && openCvOk == null) {
            openCvOk = OpenCvBootstrap.ensureLoaded()
        }
    }
    LaunchedEffect(openCvOk) {
        if (openCvOk == false) {
            onOutcome(
                Failure(
                    HandMeasureError(
                        code = HandMeasureError.Code.OPENCV_INIT_FAILED,
                        message = OpenCvBootstrap.lastErrorMessage() ?: "Failed to load OpenCV native library.",
                        recoverable = false,
                    ),
                ),
            )
        }
    }
    if (openCvOk != true) {
        // Wait for OpenCV init or finishWith(Failure(...)) above.
        return
    }

    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val cameraController = remember { CameraController(context) }
    LaunchedEffect(Unit) {
        // Avoid cropping the camera image; user may rotate to include both hand + reference card in frame.
        previewView.scaleType = androidx.camera.view.PreviewView.ScaleType.FIT_CENTER
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = viewModel.analyzer,
            onError = { t ->
                onOutcomeState.value(
                    Failure(
                        HandMeasureError(
                            code = HandMeasureError.Code.CAMERA_UNAVAILABLE,
                            message = t.message ?: "Camera binding failed.",
                            recoverable = true,
                        ),
                    ),
                )
            },
        )
        onDispose {
            cameraController.shutdown()
        }
    }

    LaunchedEffect(uiState.isProcessing) {
        if (uiState.isProcessing) {
            // Stop camera/analysis while computing to reduce CPU and avoid re-captures.
            cameraController.unbind()
        }
    }

    HandMeasureScreen(
        uiState = uiState,
        previewView = previewView,
        onCancelClick = { onCancel(CancelReason.USER) },
    )
}

private fun mapResult(size: SizeResult, request: HandMeasureRequest): MeasurementResult {
    val score = size.confidence.coerceIn(0f, 1f)
    val level = when {
        score >= 0.75f -> com.resources.handmeasure.sdk.api.Confidence.Level.HIGH
        score >= 0.45f -> com.resources.handmeasure.sdk.api.Confidence.Level.MEDIUM
        else -> com.resources.handmeasure.sdk.api.Confidence.Level.LOW
    }
    val confidence = com.resources.handmeasure.sdk.api.Confidence(score = score, level = level)

    val ringSize = RingSize(value = size.ringSizeSuggestion, numeric = null)
    val recommendation = RingSizeRecommendation(
        system = request.config.preferredSizeSystem,
        recommendedSize = ringSize,
        range = RingSizeRange(min = ringSize, max = ringSize),
        alternatives = emptyList(),
        notes = null,
    )

    val warnings = size.reasonsFail.mapNotNull { mapWarning(it) }
    val debug =
        if (request.config.debugEnabled) {
            val validFrames = size.debugMetrics["validFrames"] ?: 0.0
            MeasurementDebug(
                selectedFrameCount = validFrames.toInt(),
                usedFrameCount = validFrames.toInt(),
                meanWidthMm = size.debugMetrics["medianWidthMm"] ?: size.fingerWidthMm,
                stdWidthMm = size.debugMetrics["widthStdDev"] ?: 0.0,
                meanMmPerPx = size.mmPerPx,
                reasonsFail = size.reasonsFail,
                qualityScoreSummary = emptyMap(),
            )
        } else {
            null
        }

    return MeasurementResult(
        sessionId = System.currentTimeMillis().toString(),
        recommended = recommendation,
        confidence = confidence,
        warnings = warnings,
        debug = debug,
    )
}

private fun mapWarning(reason: String): MeasurementWarning? =
    when (reason) {
        QualityFailReason.CARD_NOT_FOUND.name, QualityFailReason.ROI_BAD.name -> MeasurementWarning.REFERENCE_NOT_FOUND
        QualityFailReason.CARD_LOW_CONF.name -> MeasurementWarning.REFERENCE_LOW_CONFIDENCE
        QualityFailReason.NO_HAND.name -> MeasurementWarning.HAND_NOT_FOUND
        QualityFailReason.LOW_CONF.name -> MeasurementWarning.HAND_LOW_CONFIDENCE
        QualityFailReason.MOTION_HIGH.name -> MeasurementWarning.HIGH_MOTION
        QualityFailReason.BLUR_LOW.name -> MeasurementWarning.BLURRY
        MeasurementFailReason.CARD_NOT_FOUND.name, MeasurementFailReason.SCALE_FAIL.name -> MeasurementWarning.REFERENCE_NOT_FOUND
        MeasurementFailReason.HAND_NOT_FOUND.name -> MeasurementWarning.HAND_NOT_FOUND
        MeasurementFailReason.HAND_LOW_CONF.name -> MeasurementWarning.HAND_LOW_CONFIDENCE
        MeasurementFailReason.HAND_NOT_STABLE.name -> MeasurementWarning.HAND_POSE_UNSTABLE
        MeasurementFailReason.WIDTH_FAIL.name -> MeasurementWarning.NOT_ENOUGH_VALID_FRAMES
        MeasurementFailReason.NOT_ENOUGH_STABLE_FRAMES.name,
        MeasurementFailReason.NOT_ENOUGH_VALID_FRAMES.name,
        -> MeasurementWarning.NOT_ENOUGH_VALID_FRAMES
        else -> null
    }

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cần quyền camera để đo tay")
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(onClick = onCancel) { Text("Hủy") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onRequest) { Text("Cho phép") }
            }
        }
    }
}

@Composable
private fun HandMeasureScreen(
    uiState: QualityUiState,
    previewView: androidx.camera.view.PreviewView,
    onCancelClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        HandStatusOverlay(
            uiState = uiState,
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(16.dp),
        )

        if (uiState.isProcessing) {
            ProcessingOverlay(
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
            Button(onClick = onCancelClick) { Text("Đóng") }
        }
    }
}

@Composable
private fun ProcessingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp), color = Color.White)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Đang tính toán kích thước...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Vui lòng giữ yên trong giây lát",
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}

@Composable
private fun HandStatusOverlay(uiState: QualityUiState, modifier: Modifier = Modifier) {
    val m = uiState.metrics

    val hasHand = m != null && !m.reasonsFail.contains(QualityFailReason.NO_HAND.name)
    val handOk =
        m != null &&
            !m.reasonsFail.contains(QualityFailReason.NO_HAND.name) &&
            !m.reasonsFail.contains(QualityFailReason.LOW_CONF.name)

    val handStatus =
        when {
            m == null -> "Đang khởi tạo..."
            m.reasonsFail.contains(QualityFailReason.NO_HAND.name) -> "Chưa thấy tay"
            m.reasonsFail.contains(QualityFailReason.LOW_CONF.name) -> "Tay chưa rõ"
            else -> "Đã thấy tay"
        }

    val cardOk =
        m != null &&
            !m.reasonsFail.contains(QualityFailReason.CARD_NOT_FOUND.name) &&
            !m.reasonsFail.contains(QualityFailReason.CARD_LOW_CONF.name)

    val cardStatus =
        when {
            m == null -> "Đang khởi tạo..."
            !hasHand -> "Chưa kiểm tra"
            m.reasonsFail.contains(QualityFailReason.CARD_NOT_FOUND.name) -> "Chưa thấy thẻ"
            m.reasonsFail.contains(QualityFailReason.CARD_LOW_CONF.name) -> "Thẻ chưa rõ"
            else -> "Đã thấy thẻ"
        }

    val confText =
        if (m == null) {
            null
        } else {
            "conf=${"%.2f".format(m.confidence)}  q_conf=${"%.2f".format(m.q_conf)}"
        }

    val cardConfText =
        if (m?.cardConfidence == null) {
            null
        } else {
            "card_conf=${"%.2f".format(m.cardConfidence)}"
        }

    Column(
        modifier =
            modifier
                .wrapContentWidth()
                .background(Color(0x80000000))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "Bắt tay: $handStatus",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Bắt thẻ: $cardStatus",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (confText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = confText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (cardConfText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cardConfText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (m != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Q_total=${"%.2f".format(m.Q_total)}  state=${uiState.state.name}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            if (m.reasonsFail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "reasons=${m.reasonsFail.joinToString("|")}",
                    color = Color(0xFFFFC107),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
