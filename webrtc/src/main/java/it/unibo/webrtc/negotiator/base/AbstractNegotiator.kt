package it.unibo.webrtc.negotiator.base

import android.util.Log
import it.unibo.webrtc.client.base.WebRtcManager
import it.unibo.webrtc.connection.base.AbstractConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Abstract negotiator.
 */
abstract class AbstractNegotiator<T: AbstractConnection> : Negotiator<T> {

    companion object {
        private val TAG = AbstractNegotiator::class.java.simpleName
    }

    //region fields
    /**
     * The connection manager.
     */
    protected abstract val connectionManager: WebRtcManager

    /**
     * The underlying connection to manage.
     */
    protected lateinit var connection: T

    /**
     * The pending candidates.
     */
    private val pendingCandidates: ConcurrentLinkedQueue<IceCandidate> = ConcurrentLinkedQueue()
    //endregion

    //region negotiator
    override fun assignConnection(connection: T) {
        this.connection = connection
    }

    override fun signalDisconnection() {
        Log.d(TAG, "Removing sinks from tracks...")
        //mark as inactive and remove local stream
        connectionManager.unbindConnection(connection)
    }
    //endregion

    //region peerconnectionobserver

    override fun onAddStream(stream: MediaStream?) {
        stream?.let { s ->
            Log.d(TAG, "Received a new stream to add: $s")
        }
    }

    override fun onRemoveStream(stream: MediaStream?) {
        stream?.let { s ->
            Log.d(TAG, "Received a new stream to remove: $s")
        }
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.d(TAG, "Created a new data channel")
    }

    //region icemanagement
    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let { c ->
            Log.d(TAG, "Received a new ICE Candidate: $c")
            pendingCandidates.add(c)
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        candidates?.let {
            Log.d(TAG, "Receiving ICE candidates to remove: $candidates")
            it.forEach { c -> pendingCandidates.remove(c) }
            connection.peerConnection.removeIceCandidates(it)
        }
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
        newState?.let { state ->
            Log.d(TAG, "Receiving ICE gathering state change: $state")

            when(state) {
                PeerConnection.IceGatheringState.NEW -> pendingCandidates.clear()
                PeerConnection.IceGatheringState.COMPLETE -> signalCandidates()
                else -> {}
            }
        }
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        newState?.let { state ->
            Log.d(TAG, "ICE Connection changed: $state")

            when(state) {
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.d(TAG, "Detected ICE disconnection, closing connection...")

                    //NOTE: the close method MUST be done in the main thread
                    GlobalScope.launch(Dispatchers.Main) { connection.close() }
                }
                else -> {}
            }
        }
    }
    //endregion

    //region ignored events
    override fun onRenegotiationNeeded() {
        Log.d(TAG, "Detected renegotiation needed")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d(TAG, "Receiving a change of an ICE connection: $receiving")
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
        newState?.let {
            Log.d(TAG, "Detected signaling state changed: $newState")
        }
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        receiver?.let { r ->
            mediaStreams?.let { m ->
                Log.d(TAG, "Received new tracks to add: $m (receiver: $r)")
            }
        }
    }
    //endregion

    //endregion

    //region helpers
    override fun dispose() {
        pendingCandidates.clear()
    }

    /**
     * Signals gathered candidates.
     */
    private fun signalCandidates() {
        Log.d(TAG, "Signaling gathered candidates...")
        try {
            val connId = connection.getConnectionId()
            var candidate = pendingCandidates.poll()
            while(candidate != null) {
                //save candidate received from ICE servers
                connection.peerConnection.addIceCandidate(candidate)
                //send the candidate to the remote peer
                connectionManager.sendIceCandidate(connId, candidate)
                //get next candidate
                candidate = pendingCandidates.poll()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to handle received ICE candidate", e)
        }
    }
    //endregion
}