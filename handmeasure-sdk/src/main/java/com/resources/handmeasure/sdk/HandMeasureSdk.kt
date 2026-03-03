package com.resources.handmeasure.sdk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.resources.handmeasure.sdk.api.HandMeasureError

object HandMeasureSdk {
    data class InitResult(val ok: Boolean, val error: HandMeasureError? = null)
    data class Diagnostics(val cameraAvailable: Boolean, val opencvReady: Boolean, val modelReady: Boolean)

    fun init(context: Context): InitResult {
        val hasCamera =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) {
            return InitResult(
                ok = false,
                error = HandMeasureError(
                    HandMeasureError.Code.CAMERA_UNAVAILABLE,
                    "No camera available on this device.",
                    recoverable = false,
                ),
            )
        }

        val permissionGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        if (!permissionGranted) {
            return InitResult(
                ok = false,
                error = HandMeasureError(
                    HandMeasureError.Code.PERMISSION_DENIED,
                    "Camera permission not granted.",
                    recoverable = true,
                ),
            )
        }

        val opencvResult = runCatching { System.loadLibrary("opencv_java4") }
        if (opencvResult.isFailure) {
            return InitResult(
                ok = false,
                error = HandMeasureError(
                    HandMeasureError.Code.OPENCV_INIT_FAILED,
                    opencvResult.exceptionOrNull()?.message,
                    recoverable = false,
                ),
            )
        }

        val modelResult = runCatching { context.assets.open("hand_landmarker.task").close() }
        if (modelResult.isFailure) {
            val error = modelResult.exceptionOrNull()
            if (error is java.io.FileNotFoundException) {
                val msg = "Missing asset: hand_landmarker.task (expected at assets/hand_landmarker.task)"
                return InitResult(
                    ok = false,
                    error = HandMeasureError(
                        HandMeasureError.Code.MODEL_LOAD_FAILED,
                        msg,
                        recoverable = false,
                    ),
                )
            }
            return InitResult(
                ok = false,
                error = HandMeasureError(
                    HandMeasureError.Code.INTERNAL_ERROR,
                    error?.message,
                    recoverable = false,
                ),
            )
        }

        return InitResult(ok = true)
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
