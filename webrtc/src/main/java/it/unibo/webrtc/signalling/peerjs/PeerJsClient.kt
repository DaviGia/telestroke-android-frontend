package it.unibo.webrtc.signalling.peerjs

import android.util.Log
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.serialization.gson.gson
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import it.unibo.webrtc.signalling.SignallingClient
import it.unibo.webrtc.signalling.observers.SignallingClientListener
import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType
import it.unibo.webrtc.signalling.peerjs.enums.SerializationType
import it.unibo.webrtc.signalling.peerjs.enums.ServerMessageType
import it.unibo.webrtc.signalling.peerjs.models.Candidate
import it.unibo.webrtc.signalling.peerjs.models.ConnectionInfo
import it.unibo.webrtc.signalling.peerjs.models.DataOfferOptions
import it.unibo.webrtc.signalling.peerjs.models.Offer
import it.unibo.webrtc.signalling.peerjs.models.PeerJsConfig
import it.unibo.webrtc.signalling.peerjs.models.ServerMessage
import it.unibo.webrtc.signalling.peerjs.util.randomToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

/**
 * The PeerJS signalling client.
 * @param options The PeerJs options.
 * @param listener The signalling client listener.
 */
class PeerJsClient(private val options: PeerJsConfig, private val listener: SignallingClientListener)
    : CoroutineScope, SignallingClient {

    companion object {
        private val TAG = PeerJsClient::class.java.simpleName

        private const val WS_ENDPOINT = "peerjs"
        private const val API_GET_ID_ENDPOINT = "api/id"
        private const val API_GET_PEERS_ENDPOINT = "api/peers"

        private const val DATA_CONNECTION_PREFIX = "dc_"
        private const val MEDIA_CONNECTION_PREFIX = "mc_"

        private const val HEARTBEAT_DELAY: Long = 20000
        private const val OFFER_TIMEOUT: Long = 20000
    }

    //region fields

    /**
     * Determines whether a connection to the server is opened or not.
     */
    private var isConnected: Boolean = false
    /**
     * Determines whether the websocket connection should be closed or not.
     */
    private var shouldClose: Boolean = false

    /**
     * The authorization parameters for the server.
     */
    private var authParams: Array<Pair<String, String?>>? = null
    /**
     * The current peer id.
     */
    private var currentId: String? = null
    /**
     * Collection of all currently active connections.
     */
    private val connections: ConcurrentHashMap<String, MutableList<ConnectionInfo>> = ConcurrentHashMap()
    /**
     * Collection of pending offers (key is peerId).
     */
    private val pendingOffers = ConcurrentHashMap<String, CompletableDeferred<SessionDescription>>()

    /**
     * Creates a new job and use it by default (so later can be freed)
     */
    private val job = Job()
    /**
     * Initialize gson instance.
     */
    private val serializer = Gson()

    /**
     * The outgoing channel, it's the send channel where messages can be put in order to send them.
     */
    private val outgoingChannel = MutableSharedFlow<String>()

    /**
     * Initializes the http client with all required features.
     */
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
        install(ContentNegotiation) {
            gson()
        }
    }

    /**
     * Overrides the default coroutine context.
     */
    override val coroutineContext = Dispatchers.IO + job
    //endregion

    //region actions

    override suspend fun connect(peerId: String?): String = suspendCoroutine { cont ->
        if (connected()) throw UnsupportedOperationException("Unable to connect when a connection is still active")

        //if no peer id is provided ask to the server
        this.currentId = peerId.takeIf { !it.isNullOrEmpty() } ?: askForUniqueId()
        //connect to the server
        connectToServer(this.currentId!!, cont)
    }

    override fun disconnect() {
        if (!connected()) throw UnsupportedOperationException("Unable to disconnect, no active connection")

        Log.d(TAG, "Disconnecting the client...")

        //force websocket disconnection
        shouldClose = true
    }

    override fun connected(): Boolean = isConnected && !shouldClose

    override suspend fun call(peerId: String, description: SessionDescription, consumer: (String) -> Unit): SessionDescription {
        Log.d(TAG, "Calling remote peer $peerId...")
        return sendOffer(peerId, description, ConnectionType.Media, consumer)
    }

    override suspend fun exchangeData(peerId: String, description: SessionDescription, consumer: (String) -> Unit): SessionDescription {
        Log.d(TAG, "Requesting data exchange to remote peer $peerId...")
        return sendOffer(peerId, description, ConnectionType.Data, consumer)
    }

    override fun answer(connectionId: String, description: SessionDescription) {
        if (!connected()) throw UnsupportedOperationException("No connection available")

        val connInfo = getConnectionInfo(connectionId)
        connInfo?.let {
            val payload = serializer.toJsonTree(Offer.buildOffer(description, connInfo.type, connInfo.id)).asJsonObject
            send(ServerMessage(ServerMessageType.Answer, payload, currentId, connInfo.peerId))
        } ?: run {
            Log.d(TAG, "Unable to reply because the connection was not found")
            throw IllegalStateException("Unable to answer to the remote request because connection was not found")
        }
    }

    override fun sendCandidate(connectionId: String, candidate: IceCandidate) {
        if (!connected()) throw UnsupportedOperationException("No connection available")

        val connInfo = getConnectionInfo(connectionId)
        connInfo?.let {
            val payload = serializer.toJsonTree(Candidate.buildCandidate(candidate, connInfo.type, connInfo.id)).asJsonObject
            send(ServerMessage(ServerMessageType.Candidate, payload, currentId, connInfo.peerId))
        } ?: run {
            Log.d(TAG, "Unable to reply because the connection was not found")
            throw IllegalStateException("Unable to send the candidate because connection was not found")
        }
    }

    //endregion

    //region websocket management

    /**
     * Connects to the signalling server.
     * @param peerId The peer id.
     * @param continuation The continuation that will be released as soon as the setup is completed or an error is occurred.
     */
    private fun connectToServer(peerId: String, continuation: Continuation<String>) = launch {
        Log.d(TAG, "Connecting...")

        authParams = arrayOf("key" to options.apiKey, "id" to peerId, "token" to randomToken())

        try {
            val protocol = if (options.secure) "wss" else "ws"
            val path = "${options.apiUrl.trimEnd('/')}/$WS_ENDPOINT"

            client.webSocket({
                method = HttpMethod.Get
                url(protocol, options.host, options.port, path)
                authParams!!.forEach { parameter(it.first, it.second) }
            }) {

                val guid = UUID.randomUUID()
                Log.d(TAG, "[$guid] Connection established")

                var failureReason: Throwable = Exception("Unknown error occurred")

                //ensure that the server replied with on
                val firstFrame = incoming.receive()
                if (firstFrame is Frame.Text) {
                    val firstMessage = serializer.fromJson(firstFrame.readText(), ServerMessage::class.java)
                    val cause: String? = when (firstMessage.type) {
                        ServerMessageType.Open -> null
                        ServerMessageType.Error -> firstMessage.payload?.get("msg")?.toString() ?: "unknown"
                        ServerMessageType.IdTaken -> firstMessage.payload?.get("msg")?.toString() ?: "ID already taken"
                        ServerMessageType.InvalidKey -> firstMessage.payload?.get("msg")?.toString() ?: "Invalid key"
                        else -> "Unknown message received from server: $firstMessage"
                    }

                    cause?.let {
                        Log.e(TAG, "[$guid] Server returned an error: $it")
                        withContext(Dispatchers.Main) {
                            listener.onConnectionError(it)
                        }
                        failureReason = IllegalStateException(it)
                    } ?: run {
                        Log.d(TAG, "[$guid] Connection opened")

                        withContext(Dispatchers.Main) {
                            listener.onConnectionOpen()
                        }

                        //change connection status
                        isConnected = true
                        continuation.resumeWith(Result.success(peerId))
                    }
                } else {
                    Log.e(TAG, "[$guid] Server returned an unexpected frame: $firstFrame")
                    withContext(Dispatchers.Main) {
                        listener.onConnectionError("Unknown response from server")
                    }

                    failureReason = IllegalStateException("Server replied with an unexpected frame")
                }

                if (isConnected) {
                    //schedule heartbeat
                    val heartBeatJob = scheduleHeartbeat()
                    try {
                        while (isActive && !shouldClose) {
                            //send data
                            outgoingChannel.first().let {
                                Log.v(TAG, "[$guid] Sending: $it")
                                outgoing.send(Frame.Text(it))
                            }

                            //get incoming data
                            incoming.tryReceive().getOrNull()?.let { frame ->
                                when(frame) {
                                    is Frame.Text -> {
                                        withContext(Dispatchers.Main) {
                                            Log.v(TAG, "[$guid] Received message: ${frame.readText()}")
                                            handleMessage(frame.readText())
                                        }
                                    }
                                    is Frame.Binary -> Log.d(TAG, "[$guid] Received binary data: $frame")
                                    else -> Log.d(TAG, "[$guid] Received unsupported frame from websocket (${frame.frameType})")
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        Log.e(TAG,"[$guid] Websocket channel closed", e)
                    } catch (e: Throwable) {
                        Log.e(TAG,"[$guid] Websocket channel error", e)
                    } finally {
                        //reset current statuses and information
                        isConnected = false
                        shouldClose = false
                        authParams = null
                        currentId = null
                        //cancel heartbeat
                        heartBeatJob.cancel()
                        //clear pending offers
                        pendingOffers.clear()
                        //clear connections
                        connections.clear()
                    }
                } else {
                    continuation.resumeWith(Result.failure(failureReason))
                }

                Log.d(TAG, "[$guid] Connection closed")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to connect to the server", e)
            continuation.resumeWith(Result.failure(e))
        }

        Log.d(TAG, "Websocket client closed")
        withContext(Dispatchers.Main) {
            listener.onConnectionClosed()
        }
    }

    /**
     * Sends data.
     * @param data The data to be sent.
     */
    private fun send(data: Any) = runBlocking {
        Log.v(TAG, "Sending message...")
        outgoingChannel.tryEmit(serializer.toJson(data))
    }

    /**
     * Sends an offer.
     * @param peerId The remote peer id
     * @param description The session description
     * @param type The connection type
     * @param consumer A function that consumes the uniquely generated connection id.
     * The session description that represents the answer of the remote peer.
     */
    private suspend fun sendOffer(peerId: String, description: SessionDescription, type: ConnectionType, consumer: (String) -> Unit): SessionDescription {

        if (!connected()) throw UnsupportedOperationException("No connection available")
        if (pendingOffers.containsKey(peerId)) throw IllegalStateException("Another call to the specified peer is still pending")

        //generate a new random connection id
        val connectionId = "${if (type == ConnectionType.Media) MEDIA_CONNECTION_PREFIX else DATA_CONNECTION_PREFIX}${randomToken()}"
        consumer(connectionId)
        //immediately save peer and connection identifiers (ICE handshaking requires the peer to send candidates)
        saveConnection(peerId, connectionId, type)

        val offer = serializer.toJsonTree(Offer.buildOffer(description, type, connectionId)).asJsonObject

        //force json serialization
        if (type == ConnectionType.Data) {
            val extra = serializer.toJsonTree(DataOfferOptions()).asJsonObject

            //merge
            extra.entrySet().forEach {
                offer.add(it.key, it.value)
            }
        }

        val outMessage = ServerMessage(ServerMessageType.Offer, offer, currentId, peerId)
        Log.d(TAG, "Sending offer...")
        send(outMessage)

        //create completable
        val completable = CompletableDeferred<SessionDescription>()
        //add to pending offers collection
        pendingOffers[peerId] = completable

        Log.d(TAG, "Waiting the answer...")
        //wait for the completion or timeout
        val answer = withTimeoutOrNull(OFFER_TIMEOUT) {
            completable.await()
        }

        //remove current peerId
        pendingOffers.remove(peerId)

        return answer ?: run {
            //remove connection if the handshaking failed
            connections.remove(connectionId)
            throw TimeoutException("No answer received after ${OFFER_TIMEOUT}ms")
        }
    }

    /**
     * Handles an incoming message.
     * @param data The message data.
     */
    private fun handleMessage(data: String) {
        Log.v(TAG, "Received: $data")

        val message = serializer.fromJson(data, ServerMessage::class.java)
        when(message.type) {
            ServerMessageType.Candidate -> handleCandidate(message) //new ice candidate (peer)
            ServerMessageType.Offer -> handleOffer(message)  // we should consider switching this to CALL/CONNECT, but this is the least breaking option.
            ServerMessageType.Answer -> handleAnswer(message) // received an answer from called remote peer
            ServerMessageType.Leave -> Log.d(TAG, "A peer left") // Another peer has closed its connection to this peer.
            ServerMessageType.Expire -> Log.d(TAG, "An offer has expired") // The offer sent to a peer has expired without response.
            ServerMessageType.Error -> Log.d(TAG, "Received an error from the server: $message")
            else -> Log.d(TAG, "Received unknown message from server: $message")
        }
    }

    /**
     * Handles a received offer.
     * @param offer The server message that represents the offer.
     */
    private fun handleOffer(offer: ServerMessage) {
        Log.d(TAG, "Received a new offer")

        try {
            val peerId = offer.src!!
            val payload = serializer.fromJson(offer.payload, Offer::class.java)
            val connectionId = payload.connectionId

            //check serialization
            if (payload.type == ConnectionType.Data) {
                val extra = offer.payload?.let {
                    serializer.fromJson(it, DataOfferOptions::class.java)
                } ?: DataOfferOptions(serialization = SerializationType.Binary)

                if (extra.serialization != SerializationType.JSON) {
                    throw IllegalArgumentException("PeerJS binary serialization type is not supported. Please use 'json' serialization.")
                }
            }

            saveConnection(peerId, connectionId, payload.type)

            payload.getSessionDescription()?.let {
                //notify listener
                listener.onOfferReceived(peerId, connectionId, it, payload.type)
            } ?: run {
                Log.d(TAG, "Unable to parse OFFER message")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error while parsing offer", e)
        }
    }

    /**
     * Handles a received answer.
     * @param answer The server message that represents the answer.
     */
    private fun handleAnswer(answer: ServerMessage) {
        Log.d(TAG, "Received an answer")

        try {
            val peerId = answer.src!!
            val payload = serializer.fromJson(answer.payload, Offer::class.java)
            val connectionId = payload.connectionId

            payload.getSessionDescription()?.let {
                //check if a pending offer is waiting for this answer
                pendingOffers[peerId]!!.complete(it)
                //notify listener
                listener.onAnswerReceived(peerId, connectionId, it, payload.type)
            } ?: run {
                Log.d(TAG, "Unable to parse ANSWER message")
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error while parsing answer", e)
        }
    }

    /**
     * Handles the signalling of a new ICE candidate.
     * @param candidate The server message that represents the candidate.
     */
    private fun handleCandidate(candidate: ServerMessage) {
        Log.d(TAG, "Received a new ICE candidate")

        try {
            val peerId = candidate.src!!
            val payload = serializer.fromJson(candidate.payload, Candidate::class.java)
            val connectionId = payload.connectionId

            if (!connectionExists(peerId, connectionId)) {
                throw IllegalStateException("Cannot add candidate to a non-existing connection")
            }

            payload.getCandidate()?.let {
                listener.onIceCandidateReceived(peerId, connectionId, it)
            } ?: run {
                Log.d(TAG, "Unable to parse CANDIDATE message")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error while parsing candidate", e)
        }
    }

    /**
     * Schedule the heartbeat.
     * @param period The heartbeat send period.
     */
    private fun scheduleHeartbeat(period: Long = HEARTBEAT_DELAY) = launch {

        val heartbeatMessage = ServerMessage(ServerMessageType.Heartbeat)

        while (isConnected) {
            try {
                delay(period)
                send(heartbeatMessage)
            } catch (e: Throwable) {
                Log.e(TAG, "Heartbeat error", e)
            }
        }
    }
    //endregion

    //region server api
    /**
     * Asks to the server for a unique id.
     * @return The unique id.
     */
    private fun askForUniqueId(): String = runBlocking {
        val response = client.get("${options.buildUrl()}/$API_GET_ID_ENDPOINT")
        response.body<String>()
    }

    override fun getActivePeers(): List<String> = runBlocking {
        if (!connected()) throw IllegalStateException("Not connected to signalling server")

        val response = client.get {
            url("${options.buildUrl()}/$API_GET_PEERS_ENDPOINT")
            authParams!!.forEach { parameter(it.first, it.second) }
        }
        response.body<List<String>>().filter { it != currentId }
    }

    //endregion

    //region helpers
    override fun dispose() {
        client.close()
        job.complete()
        connections.clear()
    }

    /**
     * Saves the connection.
     * @param peerId The remote peer id.
     * @param connectionId The connection id.
     * @param type The connection type
     */
    private fun saveConnection(peerId: String, connectionId: String, type: ConnectionType) {

        if (connectionExists(peerId, connectionId)) {
            throw UnsupportedOperationException("Offer received for existing Connection ID")
        }

        val connInfo = ConnectionInfo(connectionId, type, peerId)
        connections[peerId]?.add(connInfo) ?: run {
            connections[peerId] = mutableListOf(connInfo)
        }
    }

    /**
     * Determines whether the specified connection exists or not.
     * @param peerId The remote peer id.
     * @param connectionId The connection id.
     * @return True, if the connection exists; otherwise false.
     */
    private fun connectionExists(peerId: String, connectionId: String): Boolean {
        return connections.filter { it.key == peerId }.flatMap { it.value }.any { it.id == connectionId }
    }

    /**
     * Gets the information of a specific connection.
     * @param connectionId The connection id.
     * @return The connection information, if found.
     */
    private fun getConnectionInfo(connectionId: String) : ConnectionInfo? {
        return connections.values.flatMap { it.asIterable() }.find { it.id == connectionId }
    }
    //endregion
}