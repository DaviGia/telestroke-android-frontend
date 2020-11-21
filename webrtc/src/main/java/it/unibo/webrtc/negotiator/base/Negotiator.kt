package it.unibo.webrtc.negotiator.base

import it.unibo.webrtc.common.Disposable
import it.unibo.webrtc.connection.base.AbstractConnection
import org.webrtc.PeerConnection

/**
 * Generic RTC Negotiator interface.
 */
interface Negotiator<T: AbstractConnection> : PeerConnection.Observer, Disposable {

    /**
     * Assigns a connection to the negotiator.
     * @param connection The connection
     */
    fun assignConnection(connection: T)

    /**
     * Signals the disconnection of the underlying connection to the WebRtc manager.
     */
    fun signalDisconnection()
}