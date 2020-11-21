package it.unibo.webrtc.capture

import android.content.Context
import android.util.Log
import it.unibo.webrtc.capture.models.CaptureFormat
import it.unibo.webrtc.common.Disposable
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Camera capturer.
 * @param context The application context
 * @param eglContext The EGL context
 * @param deviceName The target camera device name (default: first front facing device)
 */
class CameraCapturer(private val context: Context, private val eglContext: EglBase.Context, private val deviceName: String? = null)
    : BaseCapturer, CameraController, Disposable {

    companion object {
        private val TAG = CameraCapturer::class.java.simpleName
        //the default video format
        private val DEFAULT_VIDEO_FORMAT = CaptureFormat(320, 240, 30)
    }

    //region fields
    /**
     * The camera device capturer.
     */
    private var videoCapturer: CameraVideoCapturer? = null
    //endregion

    //region actions
    override fun startCapture(source: VideoSource, format: CaptureFormat?) {

        //apply default format if not defined
        val captureFormat = format ?: DEFAULT_VIDEO_FORMAT

        //create the surface helper
        val surfaceTextureHelper = SurfaceTextureHelper.create(
            Thread.currentThread().name,
            eglContext
        )

        //create and initialize capturer
        videoCapturer = getCameraCapturer(deviceName).apply {
            initialize(surfaceTextureHelper, context, source.capturerObserver)
            startCapture(captureFormat.width, captureFormat.height, captureFormat.frameRate)
        }
    }

    override fun stopCapture() {
        videoCapturer?.stopCapture()
    }

    override suspend fun switchCamera(): Boolean = suspendCoroutine { cont ->
            videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {

                override fun onCameraSwitchError(error: String?) {
                    Log.e(TAG, "Unable to switch camera: $error")
                    cont.resume(false)
                }

                override fun onCameraSwitchDone(status: Boolean) {
                    Log.d(TAG, "Switch camera completed with status: $status")
                    cont.resume(status)
                }
            })
        }

    override fun changeCaptureFormat(format: CaptureFormat) {
        videoCapturer?.changeCaptureFormat(format.width, format.height, format.frameRate)
    }

    override fun dispose() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
    }
    //endregion

    //region helpers
    /**
     * Retrieves the camera capturer.
     * @param deviceName The device name (default: choose the first front facing device)
     * @throws IllegalStateException If no valid camera device is found
     */
    private fun getCameraCapturer(deviceName: String? = null): CameraVideoCapturer {

        val handler = object: CameraVideoCapturer.CameraEventsHandler {
            override fun onCameraError(error: String?) {
                Log.d(TAG, "Camera device error: $error")
            }

            override fun onCameraOpening(deviceName: String?) {
                Log.d(TAG, "Camera device opened: $deviceName")
            }

            override fun onCameraDisconnected() {
                Log.d(TAG, "Camera device disconnected")
            }

            override fun onCameraFreezed(error: String?) {
                Log.d(TAG, "Camera device freezed: $error")
            }

            override fun onFirstFrameAvailable() {
                Log.d(TAG, "Camera device made the first frame available")
            }

            override fun onCameraClosed() {
                Log.d(TAG, "Camera device closed")
            }
        }

        return Camera2Enumerator(context).run {
            deviceNames.find { it == deviceName || (deviceName.isNullOrEmpty() && isFrontFacing(it)) }?.let {
                createCapturer(it, handler)
            } ?: run {
                if (deviceName.isNullOrEmpty()) throw IllegalStateException("No valid camera device found")
                else throw IllegalArgumentException("No camera device found with name: $deviceName")
            }
        }
    }
    //endregion
}