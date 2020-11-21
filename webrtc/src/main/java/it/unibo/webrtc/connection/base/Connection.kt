package it.unibo.webrtc.connection.base

import it.unibo.webrtc.connection.enums.RtcConnectionType

/**
 * RTC connection.
 *
 * Represent a base RTC Connection object.
 */
interface Connection {

    /**
     * Gets the remote peer id.
     * @return The peer id
     */
    fun getRemotePeerId(): String

    /**
     * Gets the connection id.
     * @return The connection id
     */
    fun getConnectionId(): String

    /**
     * Retrieves the connection type.
     * @return The connection type.
     */
    fun getType(): RtcConnectionType
}