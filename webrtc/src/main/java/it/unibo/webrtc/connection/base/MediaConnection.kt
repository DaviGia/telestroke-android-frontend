package it.unibo.webrtc.connection.base

import it.unibo.webrtc.negotiator.helper.media.models.VideoTrackInfo
import kotlinx.coroutines.channels.ReceiveChannel
import org.webrtc.SurfaceViewRenderer

/**
 * RTC media connection.
 *
 * Represent a RTC Media Connection object.
 */
interface MediaConnection : ClosableConnection {

    /**
     * Sets the renderer for a remote stream video track.
     * @param trackInfo The remote video track information
     * @param renderer The surface view renderer
     * @param isMirrored Whether the renderer should render the frame flipped horizontally or not
     */
    fun setRenderer(trackInfo: VideoTrackInfo, renderer: SurfaceViewRenderer, isMirrored: Boolean = false)

    /**
     * Awaits for new remote video tracks.
     * @return The receive channel to listen for incoming remote video tracks.
     */
    suspend fun awaitVideoTracks(): ReceiveChannel<VideoTrackInfo>
}