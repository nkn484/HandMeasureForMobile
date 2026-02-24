package com.resources.handmeasure.sdk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.resources.handmeasure.sdk.api.CancelReason
import com.resources.handmeasure.sdk.api.HandMeasureOutcome
import com.resources.handmeasure.sdk.api.HandMeasureOutcome.Cancelled
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
import com.resources.handmeasure.sdk.internal.ringsize.SizeResult
import com.resources.handmeasure.sdk.internal.ui.MainViewModel
import com.resources.handmeasure.sdk.internal.ui.QualityUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = intent.getParcelableExtra(HandMeasureContract.EXTRA_REQUEST) ?: HandMeasureRequest()

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
            CoroutineScope(Dispatchers.Main).launch {
                delay(timeoutMs)
                if (!finished) finishWith(Cancelled(CancelReason.TIMEOUT))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!finished) {
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.resultVersion) {
        val result = uiState.sizeResult ?: return@LaunchedEffect
        val outcome = Success(mapResult(result, request))
        onOutcome(outcome)
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

    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val cameraController = remember { CameraController(context) }

    DisposableEffect(lifecycleOwner) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = viewModel.analyzer,
        )
        onDispose {
            cameraController.shutdown()
        }
    }

    HandMeasureScreen(
        uiState = uiState,
        previewView = previewView,
        onToggleDebug = viewModel::setDebugEnabled,
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
            MeasurementDebug(
                selectedFrameCount = size.debugMetrics["validFrames"] as? Int ?: 0,
                usedFrameCount = size.debugMetrics["validFrames"] as? Int ?: 0,
                meanWidthMm = size.debugMetrics["medianWidthMm"] as? Double ?: size.fingerWidthMm,
                stdWidthMm = size.debugMetrics["widthStdDev"] as? Double ?: 0.0,
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
        "CARD_NOT_FOUND", "ROI_BAD" -> MeasurementWarning.REFERENCE_NOT_FOUND
        "CARD_LOW_CONF" -> MeasurementWarning.REFERENCE_LOW_CONFIDENCE
        "NO_HAND" -> MeasurementWarning.HAND_NOT_FOUND
        "LOW_CONF" -> MeasurementWarning.HAND_LOW_CONFIDENCE
        "MOTION_HIGH" -> MeasurementWarning.HIGH_MOTION
        "BLUR_LOW" -> MeasurementWarning.BLURRY
        "NOT_ENOUGH_STABLE_FRAMES" -> MeasurementWarning.NOT_ENOUGH_VALID_FRAMES
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
    onToggleDebug: (Boolean) -> Unit,
    onCancelClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        GuideOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusOverlay(uiState = uiState, onToggleDebug = onToggleDebug, onCancelClick = onCancelClick)
        }
    }
}

@Composable
private fun StatusOverlay(uiState: QualityUiState, onToggleDebug: (Boolean) -> Unit, onCancelClick: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color(0x66000000))
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "State: ",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onCancelClick) {
                Text("Đóng")
            }
        }
        Text(
            text = uiState.hintText,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (uiState.progress > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = uiState.progress, modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Debug panel", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = uiState.debugEnabled, onCheckedChange = onToggleDebug)
        }

        if (uiState.debugEnabled && uiState.metrics != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color(0x55FFFFFF))
            Spacer(modifier = Modifier.height(10.dp))

            val m = uiState.metrics
            Text(
                text = "Q_total=  q_blur=  q_motion=",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "q_exposure=  q_roi=  q_conf=",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "blurVoL=  motionMAD=  meanY=  stdY=",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "clipHigh=%  clipLow=%  roiScore=",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            if (m.reasonsFail.isNotEmpty()) {
                Text(
                    text = "reasons: ",
                    color = Color(0xFFFFC107),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(horizontal = 22.dp, vertical = 70.dp)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f)
        val stroke = Stroke(width = 4f, pathEffect = dash, cap = StrokeCap.Round)

        val outerLeft = size.width * 0.05f
        val outerTop = size.height * 0.08f
        val outerWidth = size.width * 0.90f
        val outerHeight = size.height * 0.72f
        drawRoundRect(
            color = Color(0xCCFFFFFF),
            topLeft = androidx.compose.ui.geometry.Offset(outerLeft, outerTop),
            size = androidx.compose.ui.geometry.Size(outerWidth, outerHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f),
            style = stroke,
        )

        val cardWidth = outerWidth * 0.30f
        val cardHeight = cardWidth / (85.60f / 53.98f)
        val cardX = outerLeft + outerWidth * 0.08f
        val cardY = outerTop + outerHeight * 0.64f
        drawRoundRect(
            color = Color(0xB3FFD54F),
            topLeft = androidx.compose.ui.geometry.Offset(cardX, cardY),
            size = androidx.compose.ui.geometry.Size(cardWidth, cardHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = stroke,
        )

        val handPath = Path()
        val handCenterX = outerLeft + outerWidth * 0.66f
        val handCenterY = outerTop + outerHeight * 0.55f
        val handWidth = outerWidth * 0.36f
        val handHeight = outerHeight * 0.58f
        val left = handCenterX - handWidth / 2f
        val right = handCenterX + handWidth / 2f
        val top = handCenterY - handHeight / 2f
        val bottom = handCenterY + handHeight / 2f

        handPath.moveTo(left + handWidth * 0.32f, top + handHeight * 0.10f)
        handPath.quadraticBezierTo(left + handWidth * 0.22f, top, left + handWidth * 0.15f, top + handHeight * 0.15f)
        handPath.lineTo(left + handWidth * 0.14f, top + handHeight * 0.36f)
        handPath.quadraticBezierTo(left + handWidth * 0.10f, top + handHeight * 0.48f, left + handWidth * 0.16f, top + handHeight * 0.60f)
        handPath.lineTo(left + handWidth * 0.24f, top + handHeight * 0.82f)
        handPath.quadraticBezierTo(left + handWidth * 0.40f, bottom, left + handWidth * 0.58f, top + handHeight * 0.94f)
        handPath.lineTo(right - handWidth * 0.06f, top + handHeight * 0.82f)
        handPath.quadraticBezierTo(right + handWidth * 0.02f, top + handHeight * 0.56f, right - handWidth * 0.03f, top + handHeight * 0.28f)
        handPath.lineTo(right - handWidth * 0.20f, top + handHeight * 0.11f)
        handPath.quadraticBezierTo(right - handWidth * 0.30f, top - handHeight * 0.02f, right - handWidth * 0.40f, top + handHeight * 0.09f)
        handPath.lineTo(right - handWidth * 0.50f, top + handHeight * 0.23f)
        handPath.quadraticBezierTo(right - handWidth * 0.58f, top + handHeight * 0.07f, right - handWidth * 0.68f, top + handHeight * 0.15f)
        handPath.lineTo(right - handWidth * 0.74f, top + handHeight * 0.28f)
        handPath.quadraticBezierTo(right - handWidth * 0.80f, top + handHeight * 0.09f, right - handWidth * 0.90f, top + handHeight * 0.22f)
        handPath.lineTo(right - handWidth * 0.90f, top + handHeight * 0.40f)
        handPath.close()

        drawPath(
            path = handPath,
            color = Color(0xB34FC3F7),
            style = stroke,
        )
    }
}

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
