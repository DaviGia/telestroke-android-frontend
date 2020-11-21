package it.unibo.webrtc.client.models

import it.unibo.webrtc.signalling.peerjs.models.PeerJsConfig
import org.webrtc.PeerConnection

data class WebRtcOptions(val signallingOptions: PeerJsConfig, val iceServers: List<PeerConnection.IceServer>)