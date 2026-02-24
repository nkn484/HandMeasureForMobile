package com.resources.handmeasure.sdk

import android.content.Context
import com.resources.handmeasure.sdk.api.HandMeasureError

object HandMeasureSdk {
    data class InitResult(val ok: Boolean, val error: HandMeasureError? = null)
    data class Diagnostics(val cameraAvailable: Boolean, val opencvReady: Boolean, val modelReady: Boolean)

    fun init(context: Context): InitResult {
        return try {
            System.loadLibrary("opencv_java4")
            val assets = context.assets
            assets.open("hand_landmarker.task").close()
            InitResult(ok = true)
        } catch (e: Throwable) {
            InitResult(
                ok = false,
                error = HandMeasureError(HandMeasureError.Code.INTERNAL_ERROR, e.message, recoverable = false),
            )
        }
    }

    fun diagnostics(context: Context): Diagnostics {
        val cameraAvailable = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
        val opencvReady = try {
            System.loadLibrary("opencv_java4"); true
        } catch (_: Throwable) { false }
        val modelReady = try {
            context.assets.open("hand_landmarker.task").close(); true
        } catch (_: Throwable) { false }
        return Diagnostics(cameraAvailable, opencvReady, modelReady)
    }
}
