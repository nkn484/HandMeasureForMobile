package com.resources.handqualitygate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resources.handmeasure.sdk.HandMeasureContract
import com.resources.handmeasure.sdk.api.HandMeasureOutcome
import com.resources.handmeasure.sdk.api.HandMeasureConfig
import com.resources.handmeasure.sdk.api.HandMeasureRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var resultText by remember { mutableStateOf("Chưa đo") }
                    val launcher =
                        rememberLauncherForActivityResult(contract = HandMeasureContract()) { outcome ->
                            resultText =
                                when (outcome) {
                                    is HandMeasureOutcome.Success -> {
                                        val base =
                                            "Size: ${outcome.result.recommended.recommendedSize.value} (conf=${outcome.result.confidence.score})"
                                        val warnings =
                                            if (outcome.result.warnings.isEmpty()) {
                                                null
                                            } else {
                                                "Warnings: ${outcome.result.warnings.joinToString(",")}"
                                            }
                                        val reasons =
                                            outcome.result.debug?.reasonsFail?.takeIf { it.isNotEmpty() }?.let {
                                                "Reasons: ${it.joinToString("|")}"
                                            }
                                        listOfNotNull(base, warnings, reasons).joinToString("\n")
                                    }
                                    is HandMeasureOutcome.Cancelled -> "Đã hủy: ${outcome.reason}"
                                    is HandMeasureOutcome.Failure -> {
                                        val msg = outcome.error.message?.takeIf { it.isNotBlank() }
                                        if (msg == null) {
                                            "Lỗi: ${outcome.error.code}"
                                        } else {
                                            "Lỗi: ${outcome.error.code}\n$msg"
                                        }
                                    }
                                }
                        }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(resultText)
                        Button(
                            onClick = {
                                launcher.launch(
                                    HandMeasureRequest(
                                        config = HandMeasureConfig(debugEnabled = true),
                                    ),
                                )
                            },
                        ) {
                            Text("Đo size")
                        }
                    }
                }
            }
        }
    }
}
