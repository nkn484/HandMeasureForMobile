package com.resources.handmeasure.sdk.internal

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.resources.handmeasure.sdk.internal.camera.CameraController
import com.resources.handmeasure.sdk.internal.ui.MainViewModel
import com.resources.handmeasure.sdk.internal.ui.QualityUiState
import com.resources.handmeasure.sdk.internal.ui.ResultActivity

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.resultVersion) {
        val result = uiState.sizeResult ?: return@LaunchedEffect
        if (uiState.resultVersion <= 0L) return@LaunchedEffect
        context.startActivity(ResultActivity.createIntent(context, result))
    }

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
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
        PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val cameraController = remember { CameraController(context) }

    DisposableEffect(lifecycleOwner) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = viewModel.analyzer,
            onError = {},
        )
        onDispose {
            cameraController.shutdown()
        }
    }

    CameraUi(uiState = uiState, previewView = previewView, onToggleDebug = viewModel::setDebugEnabled)
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera permission is required.")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

@Composable
private fun CameraUi(
    uiState: QualityUiState,
    previewView: androidx.camera.view.PreviewView,
    onToggleDebug: (Boolean) -> Unit,
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
            StatusOverlay(uiState = uiState, onToggleDebug = onToggleDebug)
            ResultsOverlay(uiState = uiState)
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

        // Guide for ID-1 card placement (reference object).
        // This is only a visual hint (detector scans full frame) so keep it generous.
        val cardWidth = outerWidth * 0.42f
        val cardHeight = cardWidth / (85.60f / 53.98f)
        val cardX = outerLeft + outerWidth * 0.06f
        val cardY = outerTop + outerHeight * 0.60f
        drawRoundRect(
            color = Color(0xB3FFD54F),
            topLeft = androidx.compose.ui.geometry.Offset(cardX, cardY),
            size = androidx.compose.ui.geometry.Size(cardWidth, cardHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = stroke,
        )

        // More realistic (still stylized) hand silhouette guide.
        val handPath = Path()
        val handCenterX = outerLeft + outerWidth * 0.64f
        val handCenterY = outerTop + outerHeight * 0.56f
        val handWidth = outerWidth * 0.44f
        val handHeight = outerHeight * 0.70f
        val left = handCenterX - handWidth / 2f
        val top = handCenterY - handHeight / 2f

        fun p(x: Float, y: Float) =
            androidx.compose.ui.geometry.Offset(left + x * handWidth, top + y * handHeight)

        // Start at wrist (bottom-left), go clockwise.
        handPath.moveTo(p(0.38f, 0.98f).x, p(0.38f, 0.98f).y)
        handPath.cubicTo(p(0.30f, 0.96f).x, p(0.30f, 0.96f).y, p(0.24f, 0.90f).x, p(0.24f, 0.90f).y, p(0.22f, 0.82f).x, p(0.22f, 0.82f).y)
        // Thumb.
        handPath.cubicTo(p(0.18f, 0.70f).x, p(0.18f, 0.70f).y, p(0.12f, 0.60f).x, p(0.12f, 0.60f).y, p(0.16f, 0.48f).x, p(0.16f, 0.48f).y)
        handPath.cubicTo(p(0.19f, 0.41f).x, p(0.19f, 0.41f).y, p(0.23f, 0.37f).x, p(0.23f, 0.37f).y, p(0.28f, 0.34f).x, p(0.28f, 0.34f).y)
        // Valley between thumb and index.
        handPath.cubicTo(p(0.33f, 0.31f).x, p(0.33f, 0.31f).y, p(0.36f, 0.28f).x, p(0.36f, 0.28f).y, p(0.38f, 0.24f).x, p(0.38f, 0.24f).y)
        // Index finger tip and return.
        handPath.cubicTo(p(0.40f, 0.17f).x, p(0.40f, 0.17f).y, p(0.42f, 0.10f).x, p(0.42f, 0.10f).y, p(0.47f, 0.12f).x, p(0.47f, 0.12f).y)
        handPath.cubicTo(p(0.51f, 0.14f).x, p(0.51f, 0.14f).y, p(0.50f, 0.22f).x, p(0.50f, 0.22f).y, p(0.48f, 0.26f).x, p(0.48f, 0.26f).y)
        // Middle finger.
        handPath.cubicTo(p(0.49f, 0.18f).x, p(0.49f, 0.18f).y, p(0.53f, 0.06f).x, p(0.53f, 0.06f).y, p(0.59f, 0.08f).x, p(0.59f, 0.08f).y)
        handPath.cubicTo(p(0.65f, 0.10f).x, p(0.65f, 0.10f).y, p(0.63f, 0.22f).x, p(0.63f, 0.22f).y, p(0.61f, 0.28f).x, p(0.61f, 0.28f).y)
        // Ring finger.
        handPath.cubicTo(p(0.63f, 0.20f).x, p(0.63f, 0.20f).y, p(0.69f, 0.08f).x, p(0.69f, 0.08f).y, p(0.75f, 0.12f).x, p(0.75f, 0.12f).y)
        handPath.cubicTo(p(0.80f, 0.16f).x, p(0.80f, 0.16f).y, p(0.76f, 0.28f).x, p(0.76f, 0.28f).y, p(0.73f, 0.34f).x, p(0.73f, 0.34f).y)
        // Little finger.
        handPath.cubicTo(p(0.76f, 0.28f).x, p(0.76f, 0.28f).y, p(0.84f, 0.16f).x, p(0.84f, 0.16f).y, p(0.88f, 0.22f).x, p(0.88f, 0.22f).y)
        handPath.cubicTo(p(0.91f, 0.28f).x, p(0.91f, 0.28f).y, p(0.86f, 0.38f).x, p(0.86f, 0.38f).y, p(0.82f, 0.44f).x, p(0.82f, 0.44f).y)
        // Right palm down to wrist.
        handPath.cubicTo(p(0.90f, 0.58f).x, p(0.90f, 0.58f).y, p(0.84f, 0.86f).x, p(0.84f, 0.86f).y, p(0.68f, 0.94f).x, p(0.68f, 0.94f).y)
        handPath.cubicTo(p(0.60f, 0.98f).x, p(0.60f, 0.98f).y, p(0.46f, 0.99f).x, p(0.46f, 0.99f).y, p(0.38f, 0.98f).x, p(0.38f, 0.98f).y)
        handPath.close()

        drawPath(path = handPath, color = Color(0x264FC3F7))
        drawPath(path = handPath, color = Color(0xB34FC3F7), style = stroke)
    }
}

@Composable
private fun StatusOverlay(uiState: QualityUiState, onToggleDebug: (Boolean) -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color(0x66000000))
                .padding(12.dp),
    ) {
        Text(
            text = "State: ${uiState.state.name}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
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
                text = "Q_total=${m.Q_total.format(3)}  q_blur=${m.q_blur.format(2)}  q_motion=${m.q_motion.format(2)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "q_exposure=${m.q_exposure.format(2)}  q_roi=${m.q_roi.format(2)}  q_conf=${m.q_conf.format(2)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "blurVoL=${m.blurVoL.format(1)}  motionMAD=${m.motionMad.format(2)}  meanY=${m.meanY.format(1)}  stdY=${m.stdY.format(1)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    "clipHigh=${(m.pctHigh * 100).format(1)}%  clipLow=${(m.pctLow * 100).format(1)}%  roiScore=${m.roiScore.format(2)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            if (m.reasonsFail.isNotEmpty()) {
                Text(
                    text = "reasons: ${m.reasonsFail.joinToString("|")}",
                    color = Color(0xFFFFC107),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ResultsOverlay(uiState: QualityUiState) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color(0x66000000))
                .padding(12.dp),
    ) {
        Text(
            text = "Saved frames: ${uiState.savedPaths.size}",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
        if (uiState.savedPaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(140.dp),
            ) {
                items(uiState.savedPaths) { path ->
                    Text(
                        text = path,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
