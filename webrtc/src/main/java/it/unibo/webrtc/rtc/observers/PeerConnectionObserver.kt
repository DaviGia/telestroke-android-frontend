package it.unibo.webrtc.rtc.observers

import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver

/**
 * Peer connection observer.
 */
open class PeerConnectionObserver : PeerConnection.Observer {

    override fun onIceCandidate(candidate: IceCandidate?) {}

    override fun onDataChannel(dataChannel: DataChannel) {}

    override fun onIceConnectionReceivingChange(state: Boolean) {}

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

    override fun onAddStream(stream: MediaStream?) {}

    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

    override fun onRemoveStream(stream: MediaStream?) {}

    override fun onRenegotiationNeeded() {}

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
}