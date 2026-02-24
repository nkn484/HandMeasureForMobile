package com.resources.handmeasure.sdk.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class HandMeasureRequest(
    val config: HandMeasureConfig = HandMeasureConfig(),
    val sessionMetadata: Map<String, String> = emptyMap(),
) : Parcelable

@Parcelize
data class HandMeasureConfig(
    val reference: ReferenceObject = ReferenceObject.Id1Card,
    val requireReference: Boolean = true,
    val preferredSizeSystem: RingSizeSystem = RingSizeSystem.VN,
    val fitPreference: FitPreference = FitPreference.COMFORT,
    val targetFinger: Finger = Finger.RING,
    val preferredHand: HandSide = HandSide.AUTO,
    val timeoutMs: Long = 45_000L,
    val debugEnabled: Boolean = false,
) : Parcelable

sealed interface ReferenceObject : Parcelable {
    @Parcelize
data object Id1Card : ReferenceObject

    @Parcelize
data class Custom(val widthMm: Float, val heightMm: Float, val label: String) : ReferenceObject
}

@Parcelize
enum class RingSizeSystem : Parcelable { VN, US, EU, JP }

@Parcelize
enum class FitPreference : Parcelable { SNUG, COMFORT, LOOSE }

@Parcelize
enum class Finger : Parcelable { RING, INDEX, MIDDLE, LITTLE }

@Parcelize
enum class HandSide : Parcelable { LEFT, RIGHT, AUTO }

sealed interface HandMeasureOutcome : Parcelable {
    @Parcelize
    data class Success(val result: MeasurementResult) : HandMeasureOutcome

    @Parcelize
    data class Cancelled(val reason: CancelReason) : HandMeasureOutcome

    @Parcelize
    data class Failure(val error: HandMeasureError) : HandMeasureOutcome
}

@Parcelize
enum class CancelReason : Parcelable {
    USER,
    TIMEOUT,
    APP_BACKGROUND,
    PERMISSION_DENIED,
}

@Parcelize
data class HandMeasureError(
    val code: Code,
    val message: String? = null,
    val recoverable: Boolean = false,
) : Parcelable {
    @Parcelize
enum class Code : Parcelable {
        CAMERA_UNAVAILABLE,
        PERMISSION_DENIED,
        MODEL_LOAD_FAILED,
        OPENCV_INIT_FAILED,
        INTERNAL_ERROR,
    }
}

@Parcelize
data class MeasurementResult(
    val sessionId: String,
    val recommended: RingSizeRecommendation,
    val confidence: Confidence,
    val warnings: List<MeasurementWarning> = emptyList(),
    val debug: MeasurementDebug? = null,
) : Parcelable

@Parcelize
data class RingSizeRecommendation(
    val system: RingSizeSystem,
    val recommendedSize: RingSize,
    val range: RingSizeRange,
    val alternatives: List<RingSize> = emptyList(),
    val notes: String? = null,
) : Parcelable

@Parcelize
data class RingSize(
    val value: String,
    val numeric: Float? = null,
) : Parcelable

@Parcelize
data class RingSizeRange(
    val min: RingSize,
    val max: RingSize,
) : Parcelable

@Parcelize
data class Confidence(
    val score: Float,
    val level: Level,
) : Parcelable {
    @Parcelize
enum class Level : Parcelable { HIGH, MEDIUM, LOW }
}

@Parcelize
enum class MeasurementWarning : Parcelable {
    LOW_LIGHT,
    HIGH_MOTION,
    BLURRY,
    REFERENCE_NOT_FOUND,
    REFERENCE_LOW_CONFIDENCE,
    HAND_NOT_FOUND,
    HAND_LOW_CONFIDENCE,
    HAND_POSE_UNSTABLE,
    FINGER_TILT_TOO_HIGH,
    NOT_ENOUGH_VALID_FRAMES,
    HIGH_VARIANCE_RESULTS,
}

@Parcelize
data class MeasurementDebug(
    val selectedFrameCount: Int,
    val usedFrameCount: Int,
    val meanWidthMm: Double,
    val stdWidthMm: Double,
    val meanMmPerPx: Double,
    val reasonsFail: List<String> = emptyList(),
    val qualityScoreSummary: Map<String, Float> = emptyMap(),
) : Parcelable
