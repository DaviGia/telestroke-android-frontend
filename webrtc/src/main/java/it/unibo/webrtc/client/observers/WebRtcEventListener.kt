package it.unibo.webrtc.client.observers

import it.unibo.webrtc.connection.base.Connection
import it.unibo.webrtc.connection.base.DataConnection
import it.unibo.webrtc.connection.base.MediaConnection
import kotlin.coroutines.Continuation

interface WebRtcEventListener {
    /**
     * On connection opened with the signalling server.
     */
    fun onSignallingConnectionOpened()

    /**
     * On connection error or server error.
     */
    fun onSignallingConnectionError(error: String)

    /**
     * On connection closed with signalling server.
     */
    fun onSignallingConnectionClosed()

    /**
     * On call request from a remote peer.
     * @param remotePeerId The remote peer.
     * @param connection The connection.
     * @param continuation The continuation that needs to be resumed with a boolean that indicates whether to accept the request or not.
     */
    fun onCallRequest(remotePeerId: String, connection: MediaConnection, continuation: Continuation<Boolean>)

    /**
     * On data exchange request from a remote peer.
     * @param remotePeerId The remote peer.
     * @param connection The connection.
     * @param continuation The continuation that needs to be resumed with a boolean that indicates whether to accept the request or not.
     */
    fun onDataExchangeRequest(remotePeerId: String, connection: DataConnection, continuation: Continuation<Boolean>)

    /**
     * On connection closed with a remote peer.
     * @param connection The closed connection.
     */
    fun onConnectionClosed(connection: Connection)
}