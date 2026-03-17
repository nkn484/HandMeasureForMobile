package com.resources.handmeasure.sdk.internal.ringsize

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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class MediaPipeHandLandmarkerEngine(
    context: Context,
    private val modelAssetPath: String = "hand_landmarker.task",
    private val minHandDetectionConfidence: Float = 0.35f,
    private val minHandPresenceConfidence: Float = 0.35f,
    private val minTrackingConfidence: Float = 0.35f,
    private val numHands: Int = 1,
    private val maxStaleMs: Long = 350,
    private val maxBitmapDim: Int = 512,
) : HandLandmarkerEngine, AutoCloseable {
    private val appContext = context.applicationContext
    private val handLandmarkerImage: HandLandmarker? = buildLandmarker(RunningMode.IMAGE)
    private val handLandmarkerLive: HandLandmarker? = buildLandmarker(RunningMode.LIVE_STREAM)

    private val pendingBitmaps = ConcurrentHashMap<Long, Bitmap>()
    @Volatile private var latestDetection: TimedDetection? = null
    @Volatile private var lastFrameWidth: Int = 0
    @Volatile private var lastFrameHeight: Int = 0

    override fun detect(frame: FramePacket): HandDetection? {
        val landmarker = handLandmarkerImage ?: return null
        val jpeg = frame.toJpegBytes() ?: return null
        val bitmap = decodeJpeg(jpeg, maxBitmapDim) ?: return null
        val argbBitmap = ensureArgb(bitmap)

        return try {
            val mpImage = BitmapImageBuilder(argbBitmap).build()
            val result = landmarker.detect(mpImage)
            toHandDetection(result, argbBitmap.width, argbBitmap.height)
        } catch (e: Exception) {
            Log.w(TAG, "Hand landmark detect failed: ${e.message}")
            null
        } finally {
            if (argbBitmap !== bitmap) {
                bitmap.recycle()
            }
            argbBitmap.recycle()
        }
    }

    override fun detectLive(frame: FramePacket, timestampMs: Long): HandDetection? {
        val landmarker = handLandmarkerLive ?: return detect(frame)
        val jpeg = frame.toJpegBytes() ?: return getLatestDetection()
        val bitmap = decodeJpeg(jpeg, maxBitmapDim) ?: return getLatestDetection()
        val argbBitmap = ensureArgb(bitmap)
        if (argbBitmap !== bitmap) {
            bitmap.recycle()
        }

        lastFrameWidth = argbBitmap.width
        lastFrameHeight = argbBitmap.height

        return try {
            // Avoid building up a backlog: if too many frames are pending, drop this frame.
            if (pendingBitmaps.size >= 4) {
                argbBitmap.recycle()
                return latestFreshDetection(timestampMs)
            }
            val mpImage = BitmapImageBuilder(argbBitmap).build()
            pendingBitmaps[timestampMs] = argbBitmap
            landmarker.detectAsync(mpImage, timestampMs)
            latestFreshDetection(timestampMs)
        } catch (e: Exception) {
            pendingBitmaps.remove(timestampMs)?.recycle()
            Log.w(TAG, "Hand landmark live detect failed: ${e.message}")
            latestFreshDetection(timestampMs)
        }
    }

    override fun getLatestDetection(): HandDetection? = latestDetection?.detection

    override fun close() {
        try {
            handLandmarkerImage?.close()
        } catch (_: Exception) {
            // Ignore.
        }
        try {
            handLandmarkerLive?.close()
        } catch (_: Exception) {
            // Ignore.
        }
        pendingBitmaps.values.forEach { it.recycle() }
        pendingBitmaps.clear()
    }

    private fun buildLandmarker(runningMode: RunningMode): HandLandmarker? {
        return try {
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(modelAssetPath)
                    .build()

            val builder =
                HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(runningMode)
                    .setNumHands(numHands)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setMinTrackingConfidence(minTrackingConfidence)

            if (runningMode == RunningMode.LIVE_STREAM) {
                builder.setResultListener { result, input ->
                    val (width, height) = extractImageSize(input)
                    val detection = toHandDetection(result, width, height)
                    val ts = extractTimestampMs(result)
                    if (detection != null) {
                        val timestamp = ts ?: System.currentTimeMillis()
                        latestDetection = TimedDetection(timestamp, detection)
                    }
                    if (ts != null) {
                        pendingBitmaps.remove(ts)?.recycle()
                    } else if (pendingBitmaps.size > 6) {
                        val oldest = pendingBitmaps.keys.minOrNull()
                        if (oldest != null) {
                            pendingBitmaps.remove(oldest)?.recycle()
                        }
                    }
                }
                builder.setErrorListener { error ->
                    Log.w(TAG, "Hand landmarker error: ${error.message}")
                }
            }

            HandLandmarker.createFromOptions(appContext, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init HandLandmarker: ${e.message}")
            null
        }
    }

    private fun latestFreshDetection(timestampMs: Long): HandDetection? {
        val latest = latestDetection ?: return null
        if (timestampMs <= 0L) return latest.detection
        val age = max(0L, timestampMs - latest.timestampMs)
        return if (age <= maxStaleMs) latest.detection else null
    }

    private fun toHandDetection(result: Any, frameWidth: Int, frameHeight: Int): HandDetection? {
        val landmarks = (callMethod(result, "landmarks") as? List<*>)?.firstOrNull() as? List<*>
        if (landmarks.isNullOrEmpty()) return null

        val (label, score) = extractHandedness(result)
        val safeW = max(1, frameWidth)
        val safeH = max(1, frameHeight)

        val points =
            landmarks.mapNotNull { lm ->
                val x = callFloat(lm, "x") ?: callFloat(lm, "getX")
                val y = callFloat(lm, "y") ?: callFloat(lm, "getY")
                if (x == null || y == null) null else PointF(x * safeW, y * safeH)
            }

        if (points.isEmpty()) return null

        val confidences =
            landmarks.map { lm ->
                val presence = callFloat(lm, "presence") ?: callFloat(lm, "getPresence")
                val visibility = callFloat(lm, "visibility") ?: callFloat(lm, "getVisibility")
                (presence ?: visibility ?: score).coerceIn(0f, 1f)
            }

        val overallConfidence =
            if (confidences.isEmpty()) {
                score.coerceIn(0f, 1f)
            } else {
                confidences.average().toFloat().coerceIn(0f, 1f)
            }

        return HandDetection(
            landmarks2dPx = points,
            landmarkConfidences = confidences,
            handedness = label,
            confidence = overallConfidence,
        )
    }

    private fun extractHandedness(result: Any): Pair<String, Float> {
        val handedness = callMethod(result, "handedness") ?: callMethod(result, "handednesses")
        val lists = handedness as? List<*> ?: return "Unknown" to 1.0f
        val first = lists.firstOrNull() as? List<*> ?: return "Unknown" to 1.0f
        val category = first.firstOrNull() ?: return "Unknown" to 1.0f

        val label =
            callString(category, "categoryName")
                ?: callString(category, "label")
                ?: "Unknown"
        val score = callFloat(category, "score") ?: callFloat(category, "getScore") ?: 1.0f
        return label to score
    }

    private fun extractTimestampMs(result: Any): Long? {
        return callLong(result, "timestampMs") ?: callLong(result, "getTimestampMs")
    }

    private fun extractImageSize(input: Any?): Pair<Int, Int> {
        val width = callInt(input, "width") ?: callInt(input, "getWidth") ?: lastFrameWidth
        val height = callInt(input, "height") ?: callInt(input, "getHeight") ?: lastFrameHeight
        return width to height
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun decodeJpeg(jpeg: ByteArray, maxDim: Int): Bitmap? {
        if (jpeg.isEmpty()) return null
        if (maxDim <= 0) return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        val maxSide = max(w, h)
        while (maxSide / sample > maxDim) sample *= 2

        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    }

    private fun callMethod(target: Any?, name: String): Any? {
        if (target == null) return null
        return try {
            val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                ?: return null
            method.invoke(target)
        } catch (_: Exception) {
            null
        }
    }

    private fun callString(target: Any?, name: String): String? =
        (callMethod(target, name) as? String)

    private fun callFloat(target: Any?, name: String): Float? {
        val value = callMethod(target, name) ?: return null
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Number -> value.toFloat()
            else -> null
        }
    }

    private fun callLong(target: Any?, name: String): Long? {
        val value = callMethod(target, name) ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun callInt(target: Any?, name: String): Int? {
        val value = callMethod(target, name) ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Number -> value.toInt()
            else -> null
        }
    }

    private data class TimedDetection(
        val timestampMs: Long,
        val detection: HandDetection,
    )

    companion object {
        private const val TAG = "MediaPipeHandLandmarker"
    }
}
