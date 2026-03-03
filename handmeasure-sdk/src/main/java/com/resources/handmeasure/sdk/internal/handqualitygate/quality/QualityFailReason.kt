package com.resources.handmeasure.sdk.internal.quality

enum class QualityFailReason {
    NO_HAND,
    LOW_CONF,
    ROI_BAD,
    BLUR_LOW,
    MOTION_HIGH,
    EXPOSURE_CLIP_HIGH,
    EXPOSURE_CLIP_LOW,
    EXPOSURE_MEAN_OUT,
    EXPOSURE_LOW_CONTRAST,
    CARD_NOT_FOUND,
    CARD_LOW_CONF,
}

fun List<QualityFailReason>.asReasonStrings(): List<String> = map { it.name }
