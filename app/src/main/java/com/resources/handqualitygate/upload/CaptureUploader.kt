package com.resources.handqualitygate.upload

import com.resources.handqualitygate.autocapture.CapturedFrame
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

interface CaptureUploader {
    fun upload(sessionId: Long, frames: List<CapturedFrame>)
}

data class UploadConfig(
    val enabled: Boolean = true,
    val endpointUrl: String,
    val apiKey: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
)

class OkHttpCaptureUploader(
    private val config: UploadConfig,
    private val client: OkHttpClient = OkHttpClient(),
) : CaptureUploader {
    override fun upload(sessionId: Long, frames: List<CapturedFrame>) {
        if (!config.enabled) return
        if (config.endpointUrl.isBlank()) return
        if (frames.isEmpty()) return

        val metadata = buildMetadata(sessionId, frames)
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sessionId", sessionId.toString())
                .addFormDataPart("metadata", metadata)
                .apply {
                    frames.forEachIndexed { index, frame ->
                        val name = "frame_${index + 1}.jpg"
                        val requestBody = frame.jpegBytes.toRequestBody("image/jpeg".toMediaType())
                        addFormDataPart("images", name, requestBody)
                    }
                }
                .build()

        val requestBuilder =
            Request.Builder()
                .url(config.endpointUrl)
                .post(body)

        if (config.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        for ((k, v) in config.extraHeaders) {
            requestBuilder.addHeader(k, v)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: HTTP ${response.code}")
            }
        }
    }

    private fun buildMetadata(sessionId: Long, frames: List<CapturedFrame>): String {
        val framesJson =
            JSONArray().apply {
                frames.forEach { frame ->
                    put(
                        JSONObject()
                            .put("timestampMs", frame.timestampMs)
                            .put("score", frame.score)
                            .put("bytes", frame.jpegBytes.size)
                    )
                }
            }
        return JSONObject()
            .put("sessionId", sessionId)
            .put("frameCount", frames.size)
            .put("frames", framesJson)
            .toString()
    }
}

class NoopCaptureUploader : CaptureUploader {
    override fun upload(sessionId: Long, frames: List<CapturedFrame>) {
        // Intentionally left blank. Wire your API here.
    }
}
