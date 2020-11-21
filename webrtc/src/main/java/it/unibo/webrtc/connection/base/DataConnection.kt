package it.unibo.webrtc.connection.base

import it.unibo.webrtc.connection.models.ExchangeChannel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * RTC data connection.
 *
 * Represent a RTC Data Connection object.
 */
interface DataConnection: ClosableConnection {

    /**
     * Awaits for the creation of exchange channels.
     * @return The receive channel to listen for new opened exchange channels.
     */
    suspend fun awaitExchanges(): ReceiveChannel<ExchangeChannel>
}