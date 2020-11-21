package it.unibo.webrtc.client

import android.content.Context
import android.os.Looper
import android.util.Log
import io.ktor.util.KtorExperimentalAPI
import it.unibo.webrtc.capture.AudioController
import it.unibo.webrtc.capture.CameraController
import it.unibo.webrtc.capture.models.MediaOptions
import it.unibo.webrtc.client.base.WebRtcClient
import it.unibo.webrtc.client.base.WebRtcMediaManager
import it.unibo.webrtc.client.models.WebRtcOptions
import it.unibo.webrtc.client.observers.WebRtcEventListener
import it.unibo.webrtc.common.Disposable
import it.unibo.webrtc.connection.base.AbstractConnection
import it.unibo.webrtc.connection.RtcDataConnection
import it.unibo.webrtc.connection.RtcMediaConnection
import it.unibo.webrtc.connection.base.DataConnection
import it.unibo.webrtc.connection.base.MediaConnection
import it.unibo.webrtc.negotiator.RtcDataNegotiator
import it.unibo.webrtc.negotiator.RtcMediaNegotiator
import it.unibo.webrtc.rtc.RtcClient
import it.unibo.webrtc.rtc.RtcPeer
import it.unibo.webrtc.signalling.SignallingClient
import it.unibo.webrtc.signalling.observers.SignallingClientListener
import it.unibo.webrtc.signalling.peerjs.PeerJsClient
import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType
import it.unibo.webrtc.signalling.peerjs.util.randomToken
import kotlinx.coroutines.*
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.suspendCoroutine

/**
 * WebRTC peer.
 *
 * Handles all the interaction with the signalling server and the peers.
 * @param context The application context
 * @param options The WebRtcOptions
 * @param listener The WebRtcEventListener
 */
@KtorExperimentalAPI
@ExperimentalCoroutinesApi
class WebRtcPeer(private val context: Context, private val options: WebRtcOptions, private val listener: WebRtcEventListener? = null)
    : WebRtcClient, WebRtcMediaManager, SignallingClientListener, Disposable {

    companion object {
        private val TAG = WebRtcPeer::class.java.simpleName
    }

    //region fields
    /**
     * The signalling client.
     */
    private val peerClient: SignallingClient
    /**
     * The RTC client.
     */
    private val rtcClient: RtcClient

    /**
     * The list of currently active connections.
     */
    private val connections: ConcurrentHashMap<String, MutableList<AbstractConnection>> = ConcurrentHashMap()
    //endregion

    init {
        rtcClient = RtcPeer(context)
        peerClient = PeerJsClient(options.signallingOptions, this)
    }

    //region actions
    override suspend fun connect(peerId: String?): String = peerClient.connect(peerId)

    override fun disconnect() = peerClient.disconnect()

    override fun connected(): Boolean = peerClient.connected()

    override suspend fun getActivePeers(): List<String> = peerClient.getActivePeers()

    override fun initLocalMedia(options: MediaOptions, renderer: SurfaceViewRenderer?, isMirrored: Boolean) {
        rtcClient.initLocalStream(options)
        renderer?.let {
            rtcClient.initRenderer(it, isMirrored)
            val videoTrack = rtcClient.getLocalStream()?.videoTracks?.firstOrNull()
            videoTrack?.addSink(it) ?: throw IllegalStateException("Cannot initialize renderer because no local video track is available")
        }
    }

    override suspend fun call(remotePeerId: String, constraints: MediaConstraints): MediaConnection {
        Log.v(TAG, "Calling peer $remotePeerId")

        //create a new media connection
        val connection = createMediaConnection(remotePeerId).apply {
            //save the media connection
            saveConnection(remotePeerId, this, true)
        }

        try {
            connection.apply {
                //create the offer
                createOffer(constraints)?.let {
                    //send the offer to the remote peer
                    val answer = peerClient.call(remotePeerId, it) { connId ->
                        //set the connection id (so received candidates can be immediately send after the offer)
                        setConnectionId(connId)
                    }
                    //receive the remote peer answer
                    receiveAnswer(answer)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error occurred while trying to call remote peer")
            //remove connection if an error occurred in the setup
            removeConnection(remotePeerId, connection)
            throw e
        }

        return connection
    }

    override suspend fun exchangeData(remotePeerId: String, label: String?): DataConnection {
        Log.v(TAG, "Requesting data exchange to peer $remotePeerId")

        //create a new media connection
        val connection = createDataConnection(remotePeerId).apply {
            //save the media connection
            saveConnection(remotePeerId, this, true)
        }

        try {
            connection.apply {

                val channelLabel = label ?: randomToken()
                val dataChannel = peerConnection.createDataChannel(channelLabel, DataChannel.Init())

                //assign data channel to connection
                initialize(dataChannel)

                //create the offer
                createOffer()?.let {
                    //send the offer to the remote peer
                    val answer = peerClient.exchangeData(remotePeerId, it) { connId ->
                        //set the connection id (so received candidates can be immediately send after the offer)
                        setConnectionId(connId)
                    }
                    //receive the remote peer answer
                    receiveAnswer(answer)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error occurred while trying to requesting data exchange to a remote peer")
            //remove connection if an error occurred in the setup
            removeConnection(remotePeerId, connection)
            throw e
        }

        return connection
    }

    override fun getCameraController(): CameraController = rtcClient.getCameraController()

    override fun getAudioController(): AudioController = rtcClient.getAudioController()
    //endregion

    //region Signalling client listener
    override fun onConnectionOpen() {
        Log.v(TAG, "Connection opened with signalling server")
        executeOnMainThread {
            listener?.onSignallingConnectionOpened()
        }
    }

    override fun onConnectionError(error: String) {
        Log.v(TAG, "Connection error: $error")
        executeOnMainThread {
            listener?.onSignallingConnectionError(error)
        }
    }

    override fun onConnectionClosed() {
        Log.v(TAG, "Connection closed with signalling server")
        executeOnMainThread {
            listener?.onSignallingConnectionClosed()
        }
    }

    override fun onOfferReceived(remotePeerId: String, connectionId: String, offer: SessionDescription, type: ConnectionType) {
        Log.v(TAG, "Received offer for $remotePeerId ($connectionId)")

        if (connectionExists(connectionId)) throw IllegalStateException("Cannot handle offer from the specified peer; an existing connection is present")

        when(type) {
            ConnectionType.Data -> handleDataOffer(remotePeerId, connectionId, offer)
            ConnectionType.Media -> handleMediaOffer(remotePeerId, connectionId, offer)
        }
    }

    override fun onAnswerReceived(remotePeerId: String, connectionId: String, answer: SessionDescription, type: ConnectionType) {
        Log.v(TAG, "Received answer for $remotePeerId ($connectionId)")
    }

    override fun onIceCandidateReceived(remotePeerId: String, connectionId: String, iceCandidate: IceCandidate) {
        Log.v(TAG, "Received ICE candidate for $connectionId")

        getConnection(remotePeerId, connectionId)?.peerConnection?.addIceCandidate(iceCandidate)
            ?: Log.e(TAG, "Unable to set received ICE candidate because the connection was not found")
    }
    //endregion

    //region connection manager
    override fun unbindConnection(peerConnection: AbstractConnection) {
        Log.d(TAG, "Mark connection as inactive and remove local stream...")

        //remove connection from list of active connections
        removeConnection(peerConnection.getRemotePeerId(), peerConnection)

        //release local stream from media connection
        rtcClient.getLocalStream()?.let {
            peerConnection.peerConnection.removeStream(it)
        }

        executeOnMainThread { listener?.onConnectionClosed(peerConnection) }
    }

    override fun initRenderer(renderer: SurfaceViewRenderer, isMirrored: Boolean) {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            throw IllegalStateException("Renderer can be initialized only from the main thread!")
        }

        rtcClient.initRenderer(renderer, isMirrored)
    }

    override fun sendIceCandidate(connectionId: String, iceCandidate: IceCandidate) {
        peerClient.sendCandidate(connectionId, iceCandidate)
    }
    //endregion

    //region helpers
    override fun dispose() {
        if (peerClient.connected()) peerClient.disconnect()
        peerClient.dispose()

        connections.flatMap { it.value }.forEach { it.close() }
        connections.clear()

        rtcClient.dispose()
    }

    /**
     * Creates a data connection for the specified remote peer.
     * @param remotePeerId The remote peer id.
     * @return The data connection
     */
    private fun createDataConnection(remotePeerId: String): RtcDataConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(options.iceServers)
        val negotiator = RtcDataNegotiator(this)
        val connection = rtcClient.createConnection(rtcConfig, negotiator)
        return RtcDataConnection(remotePeerId, connection, negotiator)
    }

    /**
     * Creates a media connection for the specified remote peer.
     * @param remotePeerId The remote peer id.
     * @return The media connection
     */
    private fun createMediaConnection(remotePeerId: String): RtcMediaConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(options.iceServers)
        val negotiator = RtcMediaNegotiator(this)
        val connection = rtcClient.createConnection(rtcConfig, negotiator)

        //add local stream
        rtcClient.getLocalStream()?.let {
            connection.addStream(it)
        } ?: Log.i(TAG, "Created new media connection without local stream")

        return RtcMediaConnection(remotePeerId, connection, negotiator)
    }

    /**
     * Handles a data offer.
     * @param remotePeerId The remote peer id
     * @param connectionId The connection id
     * @param offer The session description
     */
    private fun handleDataOffer(remotePeerId: String, connectionId: String, offer: SessionDescription) {
        //create a new data connection
        val connection = createDataConnection(remotePeerId).apply {
            setConnectionId(connectionId)
            //add to connections
            saveConnection(remotePeerId, this)
        }

        //prepare purge function in case of failure
        val purge = {
            connection.close()
            removeConnection(remotePeerId, connection)
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                //ask to user whether to accept or deny the offer
                val response = suspendCoroutine<Boolean> { cont ->
                    listener?.onDataExchangeRequest(remotePeerId, connection, cont)
                }

                if (response) {
                    //answer to the offer
                    val remoteDescription = connection.answer(offer)
                    peerClient.answer(connectionId, remoteDescription)
                } else {
                    Log.d(TAG, "The data exchange offer has been rejected")
                    purge()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error occurred while handling received offer", e)
                purge()
            }
        }
    }

    /**
     * Handles a call offer.
     * @param remotePeerId The remote peer id
     * @param connectionId The connection id
     * @param offer The session description
     */
    private fun handleMediaOffer(remotePeerId: String, connectionId: String, offer: SessionDescription) {
        //create a new media connection
        val connection = createMediaConnection(remotePeerId).apply {
            setConnectionId(connectionId)
            //add to connections
            saveConnection(remotePeerId, this)
        }

        //prepare purge function in case of failure
        val purge = {
            connection.close()
            removeConnection(remotePeerId, connection)
        }

        GlobalScope.launch(Dispatchers.Main) {
            try {
                //ask to user whether to accept or deny the offer
                val response = suspendCoroutine<Boolean> { cont ->
                    listener?.onCallRequest(remotePeerId, connection, cont)
                }

                if (response) {
                    //answer to the offer
                    val remoteDescription = connection.answer(offer)
                    peerClient.answer(connectionId, remoteDescription)
                } else {
                    Log.d(TAG, "The call offer has been rejected")
                    purge()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error occurred while handling received offer", e)
                purge()
            }
        }
    }

    /**
     * Checks whether the connection already exists or not.
     * @param connectionId The connection id
     * @return True, if the connection exists; otherwise false
     */
    private fun connectionExists(connectionId: String): Boolean {
        return connections.values.any { it.any { i -> i.getConnectionId() == connectionId } }
    }

    /**
     * Retrieves the specified connection from the active connections collection.
     * @param peerId The peer id
     * @param connectionId The connection
     * @return The connection, if found
     */
    private fun getConnection(peerId: String, connectionId: String): AbstractConnection? {
        return connections[peerId]?.find { it.getConnectionId() == connectionId }
    }

    /**
     * Saves the connection into the active connections collection.
     * @param peerId The peer id
     * @param connection The connection
     * @param ignoreExists Whether to skip existence check or not (default: false)
     */
    private fun saveConnection(peerId: String, connection: AbstractConnection, ignoreExists: Boolean = false) {

        if (!ignoreExists && connectionExists(connection.getConnectionId())) {
            throw UnsupportedOperationException("Unable to save connection because the id is already present")
        }

        connections[peerId]?.add(connection) ?: run {
            connections[peerId] = mutableListOf(connection)
        }
    }

    /**
     * Deletes the specified connection from the active connections collection.
     * @param peerId The peer id
     * @param connection The connection
     */
    private fun removeConnection(peerId: String, connection: AbstractConnection) {
        if (connections[peerId]?.remove(connection) == false) {
            Log.i(TAG, "Connection was not removed from active connections collection (object not found)")
        }
    }

    /**
     * Executes the input function to the Main thread.
     * @param block The function to execute
     */
    private fun executeOnMainThread(block: () -> Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            block()
        }
    }
    //endregion
}