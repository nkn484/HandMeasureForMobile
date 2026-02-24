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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.resources.handmeasure.sdk.HandMeasureContract
import com.resources.handmeasure.sdk.api.HandMeasureOutcome
import com.resources.handmeasure.sdk.api.HandMeasureRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var resultText by remember { mutableStateOf("Chýa ðo") }
                    val launcher =
                        rememberLauncherForActivityResult(contract = HandMeasureContract()) { outcome ->
                            resultText = when (outcome) {
                                is HandMeasureOutcome.Success -> "Size: ${outcome.result.recommended.recommendedSize.value} (conf=${outcome.result.confidence.score})"
                                is HandMeasureOutcome.Cancelled -> "Cancelled: ${outcome.reason}"
                                is HandMeasureOutcome.Failure -> "Failure: ${outcome.error.code}"
                            }
                        }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(resultText)
                        Button(onClick = { launcher.launch(HandMeasureRequest()) }) {
                            Text("Ðo size")
                        }
                    }
                }
            }
        }
    }
}
