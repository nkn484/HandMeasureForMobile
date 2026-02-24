package com.resources.handmeasure.sdk.internal.tracking

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy

data class HandObservation(
    val roiNormalized: RectF,
    val roiPixel: Rect,
    val confidence: Float,
    val hasHand: Boolean,
)

interface HandTracker {
    fun observe(image: ImageProxy): HandObservation
}

