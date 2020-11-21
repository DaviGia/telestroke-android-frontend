package it.unibo.webrtc.negotiator.base

import it.unibo.webrtc.connection.RtcMediaConnection
import it.unibo.webrtc.negotiator.helper.media.models.VideoTrackInfo
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.SurfaceViewRenderer

/**
 * The media negotiator.
 */
interface MediaNegotiator : Negotiator<RtcMediaConnection> {

    /**
     * Sets the renderer for a remote stream.
     * @param trackInfo The remote video track information
     * @param renderer The surface view renderer
     * @param isMirrored Whether the renderer should render the frame flipped horizontally or not
     */
    fun setRenderer(trackInfo: VideoTrackInfo, renderer: SurfaceViewRenderer, isMirrored: Boolean = false)

    /**
     * Listens for remote video tracks.
     * @return The receive channel to listen for remote video tracks.
     */
    fun receiveStreams(): ReceiveChannel<VideoTrackInfo>
}