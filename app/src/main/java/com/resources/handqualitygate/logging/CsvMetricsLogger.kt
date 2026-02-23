package com.resources.handqualitygate.logging

import android.content.Context
import com.resources.handqualitygate.autocapture.AutoCaptureState
import com.resources.handqualitygate.quality.QualityResult
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

class CsvMetricsLogger(
    context: Context,
    enabled: Boolean,
) {
    private val writer: BufferedWriter?

    val file: File?

    init {
        if (!enabled) {
            writer = null
            file = null
        } else {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            file = File(dir, "quality_metrics_${System.currentTimeMillis()}.csv")
            writer = BufferedWriter(FileWriter(file, false))
            writer.write("ts_ms,state,Q_total,q_blur,q_motion,q_exposure,q_roi,q_conf,reasons\n")
            writer.flush()
        }
    }

    fun log(timestampMs: Long, state: AutoCaptureState, q: QualityResult) {
        val w = writer ?: return
        val reasons = q.reasonsFail.joinToString("|")
        val line =
            String.format(
                Locale.US,
                "%d,%s,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%s\n",
                timestampMs,
                state.name,
                q.Q_total,
                q.q_blur,
                q.q_motion,
                q.q_exposure,
                q.q_roi,
                q.q_conf,
                reasons,
            )
        w.write(line)
    }

    fun close() {
        writer?.flush()
        writer?.close()
    }
}
