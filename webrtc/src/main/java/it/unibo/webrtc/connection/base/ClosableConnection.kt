package it.unibo.webrtc.connection.base

/**
 * RTC closable connection.
 *
 * Represent a base closable RTC Connection object.
 */
interface ClosableConnection : Connection {
    /**
     * Closes the connection.
     *
     * NOTE: this method must be executed on the same thread when the PeerConnectionFactory is initialized.
     * Failed to do so will result in a deadlock of one of the threads used by the PeerConnectionFactory.
     */
    fun close()
}