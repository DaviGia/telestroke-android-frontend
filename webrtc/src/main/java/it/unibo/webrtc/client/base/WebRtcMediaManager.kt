package it.unibo.webrtc.client.base

import org.webrtc.SurfaceViewRenderer

interface WebRtcMediaManager : WebRtcManager {

    /**
     * Initializes the renderer.
     * @param renderer The surface view renderer
     * @param isMirrored Whether the renderer should render the frame flipped horizontally or not
     */
    fun initRenderer(renderer: SurfaceViewRenderer, isMirrored: Boolean = false)
}