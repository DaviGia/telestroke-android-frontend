package it.unibo.webrtc.rtc

import it.unibo.webrtc.capture.AudioController
import it.unibo.webrtc.capture.CameraController
import it.unibo.webrtc.common.Disposable
import it.unibo.webrtc.capture.models.MediaOptions
import org.webrtc.*

/**
 * Peer client interface.
 *
 * Manages the local MediaStream and creates PeerConnections.
 */
interface RtcClient : Disposable {

    /**
     * Initializes the local media stream.
     * @param options The options for the media stream
     */
    fun initLocalStream(options: MediaOptions)

    /**
     * Retrieves the local media stream.
     * @return The local media stream
     */
    fun getLocalStream(): MediaStream?

    /**
     * Initializes a renderer for the specified target stream.
     * @param renderer The surface view renderer
     * @param isMirrored Whether the playback should be flipped horizontally or not
     */
    fun initRenderer(renderer: SurfaceViewRenderer, isMirrored: Boolean = false)

    /**
     * Builds the peer connection.
     * @param configuration The RTCConfiguration
     * @param connectionObserver The PeerConnection observer
     * @return The peer connection
     */
    fun createConnection(configuration: PeerConnection.RTCConfiguration, connectionObserver: PeerConnection.Observer): PeerConnection

    /**
     * Retrieves the camera controller.
     * @return The camera controller
     */
    fun getCameraController(): CameraController

    /**
     * Retrieves the audio controller.
     * @return The audio controller
     */
    fun getAudioController(): AudioController
}