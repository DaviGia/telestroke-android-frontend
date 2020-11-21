package it.unibo.webrtc.capture

import it.unibo.webrtc.capture.models.CaptureFormat

/**
 * Camera controller.
 */
interface CameraController {
    /**
     * Switches camera on-the-fly.
     * @return True, if the switch succeeded; otherwise false.
     */
    suspend fun switchCamera(): Boolean
    /**
     * Changes the capture format on-the-fly.
     * @param format The capture format
     */
    fun changeCaptureFormat(format: CaptureFormat)
}