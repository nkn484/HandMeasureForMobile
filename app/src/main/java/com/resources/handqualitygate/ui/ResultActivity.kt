package com.resources.handqualitygate.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resources.handqualitygate.ringsize.SizeResult

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = readResult(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ResultScreen(result = result)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_MM_PER_PX = "extra_mm_per_px"
        private const val EXTRA_FINGER_WIDTH_MM = "extra_finger_width_mm"
        private const val EXTRA_RING_SIZE = "extra_ring_size"
        private const val EXTRA_CONFIDENCE = "extra_confidence"
        private const val EXTRA_REASONS = "extra_reasons"

        fun createIntent(context: Context, result: SizeResult): Intent =
            Intent(context, ResultActivity::class.java).apply {
                putExtra(EXTRA_MM_PER_PX, result.mmPerPx)
                putExtra(EXTRA_FINGER_WIDTH_MM, result.fingerWidthMm)
                putExtra(EXTRA_RING_SIZE, result.ringSizeSuggestion)
                putExtra(EXTRA_CONFIDENCE, result.confidence)
                putExtra(EXTRA_REASONS, result.reasonsFail.toTypedArray())
            }

        private fun readResult(intent: Intent): SizeResult? {
            val ringSize = intent.getStringExtra(EXTRA_RING_SIZE) ?: return null
            val reasons = intent.getStringArrayExtra(EXTRA_REASONS)?.toList().orEmpty()
            return SizeResult(
                mmPerPx = intent.getDoubleExtra(EXTRA_MM_PER_PX, 0.0),
                fingerWidthMm = intent.getDoubleExtra(EXTRA_FINGER_WIDTH_MM, 0.0),
                ringSizeSuggestion = ringSize,
                confidence = intent.getFloatExtra(EXTRA_CONFIDENCE, 0f),
                reasonsFail = reasons,
                debugMetrics = emptyMap(),
            )
        }
    }
}

@Composable
private fun ResultScreen(result: SizeResult?) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "Kết quả ước lượng", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        if (result == null) {
            Text(text = "Không có dữ liệu")
            return
        }

        Text(text = "Kích cỡ nhẫn: ${result.ringSizeSuggestion}")
        Text(text = "Độ rộng ngón tay: ${result.fingerWidthMm.format(2)} mm")
        Text(text = "Scale: ${result.mmPerPx.format(4)} mm/px")
        Text(text = "Confidence: ${(result.confidence * 100).format(1)}%")

        if (result.reasonsFail.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Lý do fail: ${result.reasonsFail.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
