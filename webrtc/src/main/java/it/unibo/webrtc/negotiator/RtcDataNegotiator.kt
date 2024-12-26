package it.unibo.webrtc.negotiator

import android.util.Log
import it.unibo.webrtc.client.base.WebRtcManager
import it.unibo.webrtc.connection.RtcDataConnection
import it.unibo.webrtc.connection.models.ExchangeChannel
import it.unibo.webrtc.negotiator.base.AbstractNegotiator
import it.unibo.webrtc.negotiator.base.DataNegotiator
import it.unibo.webrtc.negotiator.helper.data.DataChannelHelper
import it.unibo.webrtc.negotiator.helper.data.observer.DataChannelEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.DataChannel

/**
 * RTC Data Negotiator.
 *
 * Manages the negotiation with the remote peer and manages the connection state.
 */
class RtcDataNegotiator(override val connectionManager: WebRtcManager)
    : AbstractNegotiator<RtcDataConnection>(), DataNegotiator, DataChannelEventListener {

    companion object {
        private val TAG = RtcDataNegotiator::class.java.simpleName
    }

    //region fields
    /**
     * The channel used to signal new exchange channels.
     */
    private val exchangesChannel: Channel<ExchangeChannel> = Channel(Channel.UNLIMITED)
    /**
     * The data channel helpers.
     */
    private val channelHelpers: MutableList<DataChannelHelper> = mutableListOf()
    //endregion

    //region datanegotiator
    override fun receiveExchanges(): ReceiveChannel<ExchangeChannel> = exchangesChannel
    //endregion

    //region peerconnectionobserver
    override fun onDataChannel(dataChannel: DataChannel?) {
        super.onDataChannel(dataChannel)

        dataChannel?.let {
            Log.d(TAG, "Established a new data channel: ${it.id()} (label: ${it.label()})")
            channelHelpers.add(DataChannelHelper(it, this))
        } ?: Log.d(TAG, "Received empty data channel")
    }
    //endregion

    //region datachannel event listener
    override fun onChannelOpened(channel: ExchangeChannel) {
        Log.d(TAG, "Received newly opened exchange channel: ${channel.id}")

        try {
            if (!exchangesChannel.trySend(channel).isSuccess) {
                Log.e(TAG, "Unable to offer exchange channel to receive channel")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to offer exchange channel to receive channel", e)
        }
    }

    override fun onChannelClosed(channelId: Int) {
        Log.d(TAG, "Removing data channel helper because the underlying connection was closed from remote peer")
        channelHelpers.find { it.channelId() == channelId }
    }
    //endregion

    //region helpers
    override fun dispose() {
        channelHelpers.forEach { it.dispose() }
        channelHelpers.clear()

        val cause = CancellationException("Data negotiator disposed")
        exchangesChannel.close(cause)
        exchangesChannel.cancel(cause)

        super.dispose()
    }
    //endregion
}