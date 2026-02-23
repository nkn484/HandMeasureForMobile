package com.resources.handqualitygate.ui

import com.resources.handqualitygate.autocapture.AutoCaptureState
import com.resources.handqualitygate.quality.QualityResult
import com.resources.handqualitygate.ringsize.SizeResult

data class QualityUiState(
    val state: AutoCaptureState = AutoCaptureState.SEARCH,
    val progress: Float = 0f,
    val hintText: String = "Dua tay vao khung",
    val metrics: QualityResult? = null,
    val savedPaths: List<String> = emptyList(),
    val debugEnabled: Boolean = false,
    val sizeResult: SizeResult? = null,
    val resultVersion: Long = 0L,
)
