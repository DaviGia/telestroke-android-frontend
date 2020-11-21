package it.unibo.webrtc.capture.models

/**
 * The media stream parameters.
 * @param enableAudio Determines whether to enable audio or not.
 * @param enableVideo Determines whether to enable video or not.
 * @param videoCaptureDeviceName The video capture device name.
 * @param videoCaptureFormat The video capture format.
 */
data class MediaOptions(val enableAudio: Boolean = true,
                        val enableVideo: Boolean = false,
                        val videoCaptureDeviceName: String? = null,
                        val videoCaptureFormat: CaptureFormat? = null)