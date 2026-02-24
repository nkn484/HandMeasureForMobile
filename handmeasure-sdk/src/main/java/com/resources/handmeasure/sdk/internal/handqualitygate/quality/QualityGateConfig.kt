package com.resources.handmeasure.sdk.internal.quality

data class QualityGateConfig(
    val readyThreshold: Float = 0.65f,
    val stableThreshold: Float = 0.78f,
    val stableFrames: Int = 12,
    val captureDurationMs: Long = 1500L,
    val cooldownMs: Long = 1000L,
    val downsampleSize: Int = 160,
    val analysisMinIntervalMs: Long = 70L, // ~14 fps
    val aggregationWindow: Int = 12,
    val topK: Int = 10,
    val jitterThresholdPx: Float = 12f,
    val enableCsvLogging: Boolean = false,
    val requireCardForCapture: Boolean = true,
    val cardMinConfidence: Float = 0.75f,
    val cardAnalysisIntervalMs: Long = 180L,

    // Blur VoL thresholds (after downsample to 160x160).
    val blurLow: Double = 60.0,
    val blurOk: Double = 140.0,

    // Motion MAD thresholds (luma 0..255).
    val motionLow: Double = 2.0,
    val motionHigh: Double = 10.0,

    // Exposure thresholds.
    val exposureMinMean: Double = 60.0,
    val exposureMaxMean: Double = 190.0,
    val exposureMinStd: Double = 18.0,
    val exposurePctClipMax: Double = 0.12,

    // Weights for total score.
    val wBlur: Float = 0.25f,
    val wMotion: Float = 0.25f,
    val wExposure: Float = 0.20f,
    val wRoi: Float = 0.15f,
    val wConf: Float = 0.15f,
)
