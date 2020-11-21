package it.unibo.webrtc.signalling.observers

import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Signalling client listener.
 */
interface SignallingClientListener {

    /**
     * On connection with signalling server opened.
     */
    fun onConnectionOpen()
    /**
     * On connection error or server error.
     * @param error The error message
     */
    fun onConnectionError(error: String)
    /**
     * On connection with signalling server closed.
     */
    fun onConnectionClosed()

    /**
     * On offer received from remote peer.
     * @param remotePeerId The remote peer id
     * @param connectionId The connection id
     * @param offer The SDP that represents the offer
     * @param type The connection type
     */
    fun onOfferReceived(remotePeerId: String, connectionId: String, offer: SessionDescription, type: ConnectionType)

    /**
     * On answer received from remote peer.
     * @param remotePeerId The remote peer id
     * @param connectionId The connection id
     * @param answer The SDP that represents the answer
     * @param type The connection type
     */
    fun onAnswerReceived(remotePeerId: String, connectionId: String, answer: SessionDescription, type: ConnectionType)

    /**
     * On ICE candidate received.
     * @param remotePeerId The remote peer id
     * @param connectionId The connection id
     * @param iceCandidate The ICE candidate
     */
    fun onIceCandidateReceived(remotePeerId: String, connectionId: String, iceCandidate: IceCandidate)
}