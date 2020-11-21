package it.unibo.webrtc.capture

import it.unibo.webrtc.capture.models.CaptureFormat
import it.unibo.webrtc.common.Disposable
import org.webrtc.VideoSource

/**
 * Stream capturer interface.
 */
interface BaseCapturer : Disposable {

    /**
     * Starts the capture.
     * @param source The view source (to set only if you want to show the local capture)
     * @param format The capture format (default: 320x240@30)
     */
    fun startCapture(source: VideoSource, format: CaptureFormat?)
    /**
     * Stops the capture.
     */
    fun stopCapture()
}