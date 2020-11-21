package it.unibo.webrtc.rtc.observers

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Session description observer.
 */
open class SessionDescriptionObserver : SdpObserver {

    override fun onCreateSuccess(sessionDescription: SessionDescription) {}

    override fun onCreateFailure(error: String?) {}

    override fun onSetSuccess() {}

    override fun onSetFailure(error: String?) {}
}