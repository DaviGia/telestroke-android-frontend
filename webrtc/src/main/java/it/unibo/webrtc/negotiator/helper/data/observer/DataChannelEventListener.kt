package it.unibo.webrtc.negotiator.helper.data.observer

import it.unibo.webrtc.connection.models.ExchangeChannel

/**
 * Data channel event listener.
 */
interface DataChannelEventListener {

    /**
     * On data channel opened.
     * @param channel The exchange channel that wraps the underlying data channel
     */
    fun onChannelOpened(channel: ExchangeChannel)

    /**
     * On data channel closed.
     * @param channelId The channel id
     */
    fun onChannelClosed(channelId: Int)
}