package it.unibo.webrtc.negotiator

import android.util.Log
import it.unibo.webrtc.client.base.WebRtcMediaManager
import it.unibo.webrtc.connection.RtcMediaConnection
import it.unibo.webrtc.negotiator.base.AbstractNegotiator
import it.unibo.webrtc.negotiator.base.MediaNegotiator
import it.unibo.webrtc.negotiator.helper.media.models.VideoTrackInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.CancellationException

/**
 * RTC Media Negotiator.
 *
 * Manages the negotiation with the remote peer and manages the connection state.
 */
class RtcMediaNegotiator(override val connectionManager: WebRtcMediaManager): AbstractNegotiator<RtcMediaConnection>(), MediaNegotiator {

    companion object {
        private val TAG = RtcMediaNegotiator::class.java.simpleName
    }

    //region fields
    /**
     * The channel used to signal new remote stream ids.
     */
    private val streamsChannel: Channel<VideoTrackInfo> = Channel(Channel.UNLIMITED)
    /**
     * The remote streams.
     */
    private val remoteStreams: MutableList<MediaStream> = mutableListOf()
    //endregion

    //region medianegotiator
    override fun setRenderer(trackInfo: VideoTrackInfo, renderer: SurfaceViewRenderer, isMirrored: Boolean) {
        connectionManager.initRenderer(renderer, isMirrored)

        val targetTrack = remoteStreams.find { it.id == trackInfo.streamId }?.videoTracks?.find { it.id() == trackInfo.id }
        targetTrack?.addSink(renderer) ?: throw IllegalArgumentException("Cannot set renderer: specified video track not found")
    }

    override fun receiveStreams(): ReceiveChannel<VideoTrackInfo> = streamsChannel
    //endregion

    //region peerconnectionobserver
    override fun onAddStream(stream: MediaStream?) {
        super.onAddStream(stream)

        stream?.let { s ->
            //save stream
            remoteStreams.add(s)

            s.videoTracks.forEach {
                try {
                    //offer video track id
                    if (!streamsChannel.trySend(VideoTrackInfo(it.id(), s.id)).isSuccess) {
                        Log.e(TAG, "Unable to offer video track info to receive channel")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Unable to offer video track info to receive channel", e)
                }

            }
        }
    }

    override fun onRemoveStream(stream: MediaStream?) {
        super.onRemoveStream(stream)

        stream?.let { s ->
            //removes the stream
            remoteStreams.remove(s)
        }
    }
    //endregion

    //region helpers
    override fun dispose() {
        remoteStreams.clear()

        val cause = CancellationException("Media negotiator disposed")
        streamsChannel.close(cause)
        streamsChannel.cancel(cause)

        super.dispose()
    }
    //endregion
}