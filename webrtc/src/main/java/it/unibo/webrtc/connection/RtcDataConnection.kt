package it.unibo.webrtc.connection

import android.util.Log
import it.unibo.webrtc.connection.base.AbstractConnection
import it.unibo.webrtc.connection.base.DataConnection
import it.unibo.webrtc.connection.enums.RtcConnectionType
import it.unibo.webrtc.connection.models.ExchangeChannel
import it.unibo.webrtc.negotiator.base.DataNegotiator
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.DataChannel
import org.webrtc.PeerConnection

/**
 * Data connection.
 *
 * Handles the main action for a WebRTC Data Connection.
 *
 * @param peerId The peer identifier
 * @param peerConnection The peer connection
 * @param negotiator The negotiator
 */
class RtcDataConnection(peerId: String, peerConnection: PeerConnection, private val negotiator: DataNegotiator)
    : AbstractConnection(peerId, peerConnection), DataConnection {

    companion object {
        private val TAG = DataConnection::class.java.simpleName
    }

    init {
        Log.d(TAG, "Assigning this connection instance to negotiator...")
        negotiator.assignConnection(this)
    }

    override fun getType() = RtcConnectionType.Data

    //region initialization
    /**
     * Initializes the data connection with the specified data channel.
     * @param dataChannel The data channel.
     */
    fun initialize(dataChannel: DataChannel) {
        negotiator.onDataChannel(dataChannel)
    }
    //endregion

    //region dataconnection
    override suspend fun awaitExchanges(): ReceiveChannel<ExchangeChannel> = negotiator.receiveExchanges()
    //endregion

    //region helpers
    override fun close() {
        //makes the WebRtcClient to set this connection as inactive
        negotiator.signalDisconnection()
        negotiator.dispose()

        super.close()
    }
    //endregion
}