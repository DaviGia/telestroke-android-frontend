package it.unibo.webrtc.client.base

import it.unibo.webrtc.connection.base.AbstractConnection
import org.webrtc.IceCandidate

/**
 * WebRTC manager interface.
 *
 * Handles actions for WebRTC connections.
 */
interface WebRtcManager {
    /**
     * Unbinds all resources from the input connection.
     * @param peerConnection The connection
     */
    fun unbindConnection(peerConnection: AbstractConnection)

    /**
     * Sends an ICE candidate to the remote peer.
     * @param connectionId The connection id
     * @param iceCandidate The ICE Candidate
     */
    fun sendIceCandidate(connectionId: String, iceCandidate: IceCandidate)
}