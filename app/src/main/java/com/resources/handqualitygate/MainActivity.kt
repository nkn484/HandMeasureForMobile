package com.resources.handqualitygate

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
import com.resources.handqualitygate.camera.CameraController
import com.resources.handqualitygate.ui.MainViewModel
import com.resources.handqualitygate.ui.QualityUiState
import com.resources.handqualitygate.ui.ResultActivity

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
        )
        onDispose {
            cameraController.unbind()
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
