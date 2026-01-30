package com.speedwall.overlay.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages the CameraX camera session and preview for the SpeedWall Overlay app.
 *
 * This is the Android equivalent of the iOS CameraManager, converted from AVFoundation
 * to CameraX. It provides:
 * - Camera preview binding to a [PreviewView]
 * - Thread-safe latest frame capture as [Bitmap] via [ImageAnalysis]
 * - Error state observation via [StateFlow]
 * - A Composable [CameraPreviewView] for Jetpack Compose integration
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    // -- Observable state (equivalent to iOS @Published properties) -------------------

    private val _isSessionRunning = MutableStateFlow(false)
    /** Whether the camera is currently bound and running. */
    val isSessionRunning: StateFlow<Boolean> = _isSessionRunning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** The most recent error message, or null if no error has occurred. */
    val error: StateFlow<String?> = _error.asStateFlow()

    // -- Frame capture (equivalent to iOS NSLock + CGImage pattern) --------------------

    private val frameLock = ReentrantLock()
    private var _latestFrame: Bitmap? = null

    /**
     * The most recent camera frame as a [Bitmap] (thread-safe).
     *
     * Access is guarded by a [ReentrantLock], mirroring the iOS NSLock pattern.
     * Returns null if no frame has been captured yet.
     */
    val latestFrame: Bitmap?
        get() = frameLock.withLock { _latestFrame }

    // -- Internal CameraX components --------------------------------------------------

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    /** Dedicated single-thread executor for image analysis (equivalent to iOS videoDataQueue). */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // -- Public API -------------------------------------------------------------------

    /**
     * Binds the camera to the given [lifecycleOwner] and surfaces the preview on [previewView].
     *
     * This sets up two use cases:
     * 1. **Preview** -- renders the live camera feed onto the [PreviewView].
     * 2. **ImageAnalysis** -- captures every frame, converts it to a [Bitmap], and stores
     *    it in [latestFrame] for screenshot / overlay purposes.
     *
     * The method tries [CameraSelector.DEFAULT_BACK_CAMERA] first and falls back to
     * [CameraSelector.DEFAULT_FRONT_CAMERA], matching the iOS behaviour of preferring
     * the back wide-angle camera.
     */
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Build Preview use case
                preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                // Build ImageAnalysis use case (equivalent to AVCaptureVideoDataOutput)
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                // Unbind any existing use cases before rebinding
                provider.unbindAll()

                // Try back camera first, fall back to front camera
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (backCameraExc: Exception) {
                    Log.w(TAG, "Back camera unavailable, falling back to front camera", backCameraExc)
                    try {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (frontCameraExc: Exception) {
                        Log.e(TAG, "No camera available", frontCameraExc)
                        _error.value = "Camera is not available on this device"
                        _isSessionRunning.value = false
                        return@addListener
                    }
                }

                _isSessionRunning.value = true
                _error.value = null
                Log.d(TAG, "Camera bound successfully")

            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
                _error.value = "An unknown error occurred: ${exc.localizedMessage}"
                _isSessionRunning.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Unbinds all camera use cases and releases the analysis executor.
     *
     * Call this when the camera is no longer needed (e.g. in onDestroy or when the
     * composable leaves composition).
     */
    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
        } catch (exc: Exception) {
            Log.w(TAG, "Error unbinding camera provider", exc)
        }
        _isSessionRunning.value = false

        analysisExecutor.shutdown()

        // Release the latest frame bitmap
        frameLock.withLock {
            _latestFrame?.recycle()
            _latestFrame = null
        }

        Log.d(TAG, "CameraManager shut down")
    }

    // -- Frame processing (equivalent to captureOutput delegate) ----------------------

    /**
     * Processes an [ImageProxy] from the [ImageAnalysis] use case.
     *
     * Converts the YUV_420_888 image to a [Bitmap], applies any required rotation,
     * and stores the result in [_latestFrame] behind the [frameLock].
     * This mirrors the iOS captureOutput(_:didOutput:from:) delegate method.
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val rotatedBitmap = rotateBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
                frameLock.withLock {
                    // Recycle previous frame to avoid memory leaks
                    _latestFrame?.recycle()
                    _latestFrame = rotatedBitmap
                }
            }
        } catch (exc: Exception) {
            Log.w(TAG, "Failed to process image frame", exc)
        } finally {
            imageProxy.close()
        }
    }

    // -- YUV to Bitmap conversion -----------------------------------------------------

    /**
     * Converts a YUV_420_888 [ImageProxy] to an ARGB [Bitmap].
     *
     * Uses Android's [YuvImage] and [BitmapFactory] for the conversion. The planes
     * from the ImageProxy are combined into an NV21-formatted byte array which
     * [YuvImage] can decode.
     *
     * @return A [Bitmap] in ARGB_8888 format, or null if conversion fails.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            Log.w(TAG, "Unexpected image format: ${imageProxy.format}")
            return null
        }

        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21 format: Y plane followed by interleaved VU
        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Copy VU interleaved data.
        // The U and V planes in YUV_420_888 may have a pixel stride of 1 (planar)
        // or 2 (semi-planar / already interleaved as NV21 or NV12).
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        if (vPixelStride == 2 && uPixelStride == 2) {
            // Semi-planar: V and U buffers are already interleaved (NV21 or NV12).
            // On most Android devices the V buffer overlaps with U in NV21 order.
            vBuffer.rewind()
            vBuffer.get(nv21, ySize, vSize)
        } else {
            // Planar: pixel stride == 1, manually interleave V and U bytes.
            val uvWidth = imageProxy.width / 2
            val uvHeight = imageProxy.height / 2
            var offset = ySize
            val vRowStride = vPlane.rowStride
            val uRowStride = uPlane.rowStride

            val vBytes = ByteArray(vSize)
            val uBytes = ByteArray(uSize)
            vBuffer.rewind()
            uBuffer.rewind()
            vBuffer.get(vBytes)
            uBuffer.get(uBytes)

            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vIndex = row * vRowStride + col * vPixelStride
                    val uIndex = row * uRowStride + col * uPixelStride
                    if (offset < nv21.size) {
                        nv21[offset++] = vBytes[vIndex]
                    }
                    if (offset < nv21.size) {
                        nv21[offset++] = uBytes[uIndex]
                    }
                }
            }
        }

        // Convert NV21 to Bitmap via YuvImage -> JPEG -> Bitmap
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            90,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Rotates [bitmap] by [rotationDegrees] if the rotation is non-zero.
     *
     * CameraX reports the rotation needed to make the image upright via
     * ImageProxy.imageInfo.rotationDegrees. This mirrors the iOS
     * videoOrientation = .portrait connection setting that auto-rotates frames.
     *
     * @return A new rotated [Bitmap] if rotation is needed; otherwise the original bitmap.
     */
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
        // Recycle the un-rotated source if a new bitmap was created
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}

// ======================================================================================
// Jetpack Compose integration (equivalent to iOS CameraPreview: UIViewRepresentable)
// ======================================================================================

/**
 * A Composable camera preview that displays the live camera feed and binds
 * [CameraManager] to the current lifecycle.
 *
 * This is the Android equivalent of the iOS CameraPreview SwiftUI view that wraps
 * AVCaptureVideoPreviewLayer. Here we use CameraX's [PreviewView] via [AndroidView].
 *
 * @param cameraManager The [CameraManager] instance that will bind the camera.
 * @param modifier Optional [Modifier] for the composable.
 * @param onPreviewViewCreated Optional callback that receives the [PreviewView] reference,
 *        useful for capturing screenshots of the preview surface.
 */
@Composable
fun CameraPreviewView(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier,
    onPreviewViewCreated: ((PreviewView) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Notify caller of the PreviewView reference
    onPreviewViewCreated?.invoke(previewView)

    // Bind camera when entering composition, shutdown when leaving
    DisposableEffect(lifecycleOwner) {
        cameraManager.bindCamera(lifecycleOwner, previewView)
        onDispose {
            cameraManager.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
