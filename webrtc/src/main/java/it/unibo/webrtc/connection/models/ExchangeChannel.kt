package it.unibo.webrtc.connection.models

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * The exchange channel interface.
 */
interface ExchangeChannel {

    /**
     * Gets the channel id.
     * @return the channel id
     */
    val id: Int

    /**
     * Sends data.
     * @param data The data to send
     * @return True, if the message was sent correctly; otherwise false.
     */
    fun send(data: String): Boolean

    /**
     * Gets the receive channel to listen for incoming messages.
     * @return The receive channel
     */
    fun receive(): ReceiveChannel<String>
}