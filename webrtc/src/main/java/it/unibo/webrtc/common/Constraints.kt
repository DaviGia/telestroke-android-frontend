package it.unibo.webrtc.common

import org.webrtc.MediaConstraints

/**
 * Constraints
 */
object Constraints {

    object Generic {
        const val Enabled = "true"
        const val Disabled = "false"
    }

    object Video {
        const val MinAspectRatio = "minAspectRatio"
        const val MaxAspectRatio = "maxAspectRatio"
        const val MaxWidth       = "maxWidth"
        const val MinWidth       = "minWidth"
        const val MaxHeight      = "maxHeight"
        const val MinHeight      = "minHeight"
        const val MaxFrameRate   = "maxFrameRate"
        const val MinFrameRate   = "minFrameRate"

        // Google-specific constraint keys for a local video source
        const val NoiseReduction            = "googNoiseReduction"
        const val LeakyBucket              = "googLeakyBucket"
        const val TemporalLayeredScreencast = "googTemporalLayeredScreencast"
    }

    object Audio {
        // Google-specific constraint keys used by a local audio source.
        const val EchoCancellation             = "googEchoCancellation"
        const val ExperimentalEchoCancellation = "googEchoCancellation2"
        const val AutoGainControl              = "googAutoGainControl"
        const val ExperimentalAutoGainControl  = "googAutoGainControl2"
        const val NoiseSuppression             = "googNoiseSuppression"
        const val ExperimentalNoiseSuppression = "googNoiseSuppression2"
        const val HighpassFilter               = "googHighpassFilter"
        const val TypingNoiseDetection         = "googTypingNoiseDetection"
        const val AudioMirroring               = "googAudioMirroring"
    }

    object Offer {
        // Constraint keys used for offers (W3C PeerConnection spec)
        const val OfferToReceiveVideo    = "OfferToReceiveVideo"
        const val OfferToReceiveAudio    = "OfferToReceiveAudio"
        const val VoiceActivityDetection = "VoiceActivityDetection"
        const val IceRestart             = "IceRestart"
    }

    /**
     * Builds default media constraints (both video and audio enabled).
     * @return The media constraints
     */
    fun buildDefaultMediaConstraints(): MediaConstraints =
        MediaConstraints().apply {
            mandatory.addAll(listOf(
                MediaConstraints.KeyValuePair(Offer.OfferToReceiveVideo, Generic.Enabled),
                MediaConstraints.KeyValuePair(Offer.OfferToReceiveAudio, Generic.Enabled)
            ))
        }
}






