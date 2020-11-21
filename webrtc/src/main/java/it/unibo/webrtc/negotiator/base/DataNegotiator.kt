package it.unibo.webrtc.negotiator.base

import it.unibo.webrtc.connection.RtcDataConnection
import it.unibo.webrtc.connection.models.ExchangeChannel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * The data negotiator.
 */
interface DataNegotiator : Negotiator<RtcDataConnection> {

    /**
     * Listens for exchange channels.
     * @return The receive channel to listen for exchange channels.
     */
    fun receiveExchanges(): ReceiveChannel<ExchangeChannel>
}