package it.unibo.webrtc.connection.models

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking

/**
 * The exchange data channel.
 * @param id The channel id
 * @param inbound The inbound channel to listen for incoming messages.
 * @param outgoing The channel to send message.
 */
class ExchangeDataChannel(override val id: Int, private val inbound: ReceiveChannel<String>, private val outgoing: SendChannel<String>)
    : ExchangeChannel {

    override fun send(data: String): Boolean = outgoing.trySend(data).isSuccess

    override fun receive(): ReceiveChannel<String> = inbound
}