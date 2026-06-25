package com.duq.android.camera

import android.content.Context
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot JPEG capture with NO preview surface, drivable from a (non-UI)
 * service: CameraX binds an ImageCapture use case to a throwaway RESUMED
 * lifecycle, grabs a single frame in-memory, then unbinds.
 *
 * Background camera access requires the host foreground service to declare the
 * `camera` foregroundServiceType (see AndroidManifest + the FGS that raises it).
 */
class CameraCapture(private val context: Context) {

    data class Snap(val base64: String, val width: Int, val height: Int, val format: String = "jpeg")

    suspend fun snap(facingBack: Boolean): Snap = withContext(Dispatchers.Main) {
        val provider = ProcessCameraProvider.getInstance(context).awaitCompat()
        val owner = TransientLifecycleOwner().also { it.start() }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = if (facingBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, imageCapture)
            captureJpeg(imageCapture)
        } finally {
            provider.unbindAll()
            owner.stop()
        }
    }

    private suspend fun captureJpeg(imageCapture: ImageCapture): Snap =
        suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            cont.resume(Snap(b64, image.width, image.height))
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        cont.resumeWithException(exc)
                    }
                }
            )
        }

    /** Minimal LifecycleOwner kept at RESUMED for the duration of one capture. */
    private class TransientLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() { registry.currentState = Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    }
}

/** Await a ListenableFuture without pulling in extra deps. */
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitCompat(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try { cont.resume(get()) } catch (e: Exception) { cont.resumeWithException(e) }
        }, Runnable::run)
    }
