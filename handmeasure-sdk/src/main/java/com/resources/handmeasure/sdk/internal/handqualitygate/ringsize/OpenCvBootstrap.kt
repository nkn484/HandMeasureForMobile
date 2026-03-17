package com.resources.handmeasure.sdk.internal.ringsize

import org.opencv.core.Core

/**
 * Ensures the OpenCV native library is loaded before any OpenCV API is used.
 *
 * Without this, devices may crash with [UnsatisfiedLinkError], which the calling app can interpret
 * as a generic user-cancel via ActivityResult fallback.
 */
internal object OpenCvBootstrap {
    @Volatile
    private var loaded: Boolean? = null
    @Volatile
    private var lastError: Throwable? = null

    fun ensureLoaded(): Boolean {
        val cached = loaded
        if (cached != null) return cached
        return synchronized(this) {
            val again = loaded
            if (again != null) return again
            val candidates = listOf("opencv_java4", Core.NATIVE_LIBRARY_NAME).distinct()
            var ok = false
            var err: Throwable? = null
            for (name in candidates) {
                try {
                    System.loadLibrary(name)
                    ok = true
                    err = null
                    break
                } catch (t: Throwable) {
                    err = t
                }
            }
            lastError = err
            loaded = ok
            ok
        }
    }

    fun lastErrorMessage(): String? = lastError?.message
}
