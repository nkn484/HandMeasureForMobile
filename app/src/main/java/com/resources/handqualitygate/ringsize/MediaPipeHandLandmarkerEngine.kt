package com.resources.handqualitygate.ringsize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions

class MediaPipeHandLandmarkerEngine(
    context: Context,
    private val modelAssetPath: String = "hand_landmarker.task",
    private val minHandDetectionConfidence: Float = 0.5f,
    private val minHandPresenceConfidence: Float = 0.5f,
    private val minTrackingConfidence: Float = 0.5f,
    private val numHands: Int = 1,
) : HandLandmarkerEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val handLandmarker: HandLandmarker? = buildLandmarker()

    override fun detect(frame: FramePacket): HandDetection? {
        val landmarker = handLandmarker ?: return null
        val jpeg = frame.toJpegBytes() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val argbBitmap = ensureArgb(bitmap)

        return try {
            val mpImage = BitmapImageBuilder(argbBitmap).build()
            val result = landmarker.detect(mpImage)
            val landmarks = result.landmarks().firstOrNull() ?: return null

            // Keep compatibility across Tasks API versions where handedness accessors differ.
            val confidence = 1.0f
            val handLabel = "Unknown"

            val points =
                landmarks.map { lm ->
                    PointF(lm.x() * argbBitmap.width, lm.y() * argbBitmap.height)
                }
            val confidences = List(points.size) { confidence.coerceIn(0f, 1f) }

            HandDetection(
                landmarks2dPx = points,
                landmarkConfidences = confidences,
                handedness = handLabel,
                confidence = confidence.coerceIn(0f, 1f),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Hand landmark detect failed: ${e.message}")
            null
        } finally {
            if (argbBitmap !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    override fun close() {
        try {
            handLandmarker?.close()
        } catch (_: Exception) {
            // Ignore.
        }
    }

    private fun buildLandmarker(): HandLandmarker? {
        return try {
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(modelAssetPath)
                    .build()

            val options =
                HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(numHands)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setMinTrackingConfidence(minTrackingConfidence)
                    .build()

            HandLandmarker.createFromOptions(appContext, options)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init HandLandmarker: ${e.message}")
            null
        }
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    companion object {
        private const val TAG = "MediaPipeHandLandmarker"
    }
}
