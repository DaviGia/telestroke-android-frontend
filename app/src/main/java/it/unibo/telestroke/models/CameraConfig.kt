package it.unibo.telestroke.models

import it.unibo.webrtc.capture.models.CaptureFormat

data class CameraConfig(val device: String?,
                        val format: CaptureFormat)