package it.unibo.webrtc.connection

import android.util.Log
import it.unibo.webrtc.connection.base.AbstractConnection
import it.unibo.webrtc.connection.base.MediaConnection
import it.unibo.webrtc.connection.enums.RtcConnectionType
import it.unibo.webrtc.negotiator.base.MediaNegotiator
import it.unibo.webrtc.negotiator.helper.media.models.VideoTrackInfo
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

/**
 * Media connection.
 *
 * Handles the main action for a WebRTC Media Connection.
 *
 * @param peerId The peer identifier
 * @param peerConnection The peer connection
 * @param negotiator The negotiator
 */
class RtcMediaConnection(peerId: String, peerConnection: PeerConnection, private val negotiator: MediaNegotiator)
    : AbstractConnection(peerId, peerConnection), MediaConnection {
    
    companion object {
        private val TAG = MediaConnection::class.java.simpleName
    }

    init {
        Log.d(TAG, "Assigning this connection instance to negotiator...")
        negotiator.assignConnection(this)
    }

    override fun getType() = RtcConnectionType.Media

    //region rtc media connection
    override fun setRenderer(trackInfo: VideoTrackInfo, renderer: SurfaceViewRenderer, isMirrored: Boolean) {
        negotiator.setRenderer(trackInfo, renderer, isMirrored)
    }

    override suspend fun awaitVideoTracks(): ReceiveChannel<VideoTrackInfo> = negotiator.receiveStreams()

    override fun close() {
        //makes the WebRtcClient to set this connection as inactive and releases the local stream (avoid to dispose it)
        negotiator.signalDisconnection()
        negotiator.dispose()

        super.close()
    }
    //endregion
}