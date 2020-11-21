package it.unibo.webrtc.connection.base

import android.os.OperationCanceledException
import android.util.Log
import it.unibo.webrtc.rtc.observers.SessionDescriptionObserver
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Abstract connection.
 *
 * Handles the main action for a WebRTC Connection.
 *
 * @param peerId The peer identifier
 * @param peerConnection The peer connection
 */
abstract class AbstractConnection(private val peerId: String, val peerConnection: PeerConnection)
    : ConnectionManager, ClosableConnection {

    companion object {
        private val TAG = AbstractConnection::class.java.simpleName
    }

    /**
     * The identifier of the connection between the local and the remote peer.
     */
    private lateinit var connectionId: String

    //region rtc connection manager
    override fun setConnectionId(connectionId: String) {
        this.connectionId = connectionId
    }

    override suspend fun createOffer(mediaConstraints: MediaConstraints): SessionDescription? = suspendCoroutine { cont ->
        Log.d(TAG, "Creating offer...")

        peerConnection.createOffer(object : SessionDescriptionObserver() {

            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "Offer: successfully created SPD offer")

                peerConnection.setLocalDescription(object : SessionDescriptionObserver() {
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Offer: failed to set offer SDP ($error)")
                        cont.resume(null)
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "Offer: successfully set SPD offer")
                        cont.resume(sessionDescription)
                    }
                }, sessionDescription)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Offer: failed to create offer SDP ($error)")
                cont.resume(null)
            }

        }, mediaConstraints)
    }

    override suspend fun answer(offerDescription: SessionDescription, mediaConstraints: MediaConstraints): SessionDescription = suspendCoroutine { cont ->
        Log.d(TAG, "Creating answer...")

        peerConnection.setRemoteDescription(object : SessionDescriptionObserver() {

            override fun onSetSuccess() {
                Log.d(TAG, "Answer: successfully set remote SPD offer")

                peerConnection.createAnswer(object : SessionDescriptionObserver() {

                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        Log.d(TAG, "Answer: successfully created local SPD offer")

                        peerConnection.setLocalDescription(object : SessionDescriptionObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Answer: successfully set local SPD offer")
                                cont.resume(sessionDescription)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Answer: failed to set local SDP offer ($error)")
                                cont.resumeWithException(OperationCanceledException(error))
                            }
                        }, sessionDescription)
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Answer: failed to create SDP offer ($error)")
                        cont.resumeWithException(OperationCanceledException(error))
                    }

                }, mediaConstraints)
            }

            override fun onSetFailure(error: String?) {
                super.onSetFailure(error)
                Log.e(TAG, "Answer: failed to set remote SDP offer ($error)")

                cont.resumeWithException(OperationCanceledException(error ?: "unknown error"))
            }

        }, offerDescription)
    }

    override suspend fun receiveAnswer(answerDescription: SessionDescription) = suspendCoroutine<Unit> { cont ->
        Log.d(TAG, "Saving received answer...")

        peerConnection.setRemoteDescription(object : SessionDescriptionObserver() {

            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "SDP set succeeded")
                cont.resume(Unit)
            }

            override fun onSetFailure(error: String?) {
                super.onSetFailure(error)
                Log.e(TAG, "SDP set failed: $error")
                cont.resumeWithException(OperationCanceledException(error ?: "unknown error"))
            }

        }, answerDescription)
    }
    //endregion

    //region rtc connection
    override fun getRemotePeerId(): String = peerId

    override fun getConnectionId(): String = connectionId

    override fun close() {
        Log.d(TAG, "Closing connection...")
        peerConnection.dispose()
    }
    //endregion
}