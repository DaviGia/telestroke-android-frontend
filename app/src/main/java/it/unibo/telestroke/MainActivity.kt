package it.unibo.telestroke

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.gson.gson
import it.unibo.telestroke.databinding.ActivityMainBinding
import it.unibo.telestroke.models.DeviceConfig
import it.unibo.telestroke.models.messages.MessageBody
import it.unibo.telestroke.models.messages.MessageType
import it.unibo.telestroke.models.messages.data.StepInfo
import it.unibo.telestroke.models.request.AddPeerRequest
import it.unibo.telestroke.models.request.LoginRequest
import it.unibo.telestroke.models.response.LoginResponse
import it.unibo.webrtc.capture.models.MediaOptions
import it.unibo.webrtc.client.WebRtcPeer
import it.unibo.webrtc.client.base.WebRtcClient
import it.unibo.webrtc.client.models.WebRtcOptions
import it.unibo.webrtc.client.observers.WebRtcEventListener
import it.unibo.webrtc.connection.base.ClosableConnection
import it.unibo.webrtc.connection.base.Connection
import it.unibo.webrtc.connection.base.DataConnection
import it.unibo.webrtc.connection.base.MediaConnection
import it.unibo.webrtc.connection.enums.RtcConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.Camera2Enumerator
import org.webrtc.PeerConnection
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * The main activity
 */
class MainActivity : AppCompatActivity(), WebRtcEventListener {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 0
        private const val DEFAULT_RECONNECTION_DELAY = 1000L
        private const val DEFAULT_INITIAL_RECONNECTION_DELAY = 5000L
        private const val DEFAULT_MAX_RECONNECTION_ATTEMPTS = 15

        private const val LOGIN_URL = "/auth/login"
        private const val ADD_PEER_URL = "/peer/peers"
        private const val AUTHORIZATION_HEADER_KEY = "Authorization"
        private const val JWT_FORMAT = "Bearer %s"

        private val TAG = MainActivity::class.java.simpleName
    }

    //region fields
    /**
     * The device configuration.
     */
    private lateinit var config: DeviceConfig
    /**
     * The coroutine scope.
     */
    private lateinit var scope: CoroutineScope
    /**
     * The WebRTC client.
     */
    private lateinit var webRtcClient: WebRtcClient
    /**
     * The current call.
     */
    private var connections: MutableList<ClosableConnection> = mutableListOf()
    /**
     * Determines if a reconnection is already in progress.
     */
    private var reconnecting: Boolean = false
    /**
     * UI binding.
     */
    private lateinit var binding: ActivityMainBinding
    //endregion

    //region Activity event handlers
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Activity: onCreate")
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readConfig()

        if (checkPermissions()) {
            initialize()
        }
    }

    override fun onStart() {
        Log.d(TAG, "Activity: onStart")
        super.onStart()

        //create coroutine scope
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        //triggered when the app is back in foreground
        if (this::webRtcClient.isInitialized && !this.webRtcClient.connected()) {
            //disconnect from signalling server
            Log.d(TAG, "Connecting client...")
            scope.launch(Dispatchers.Main) {
                reconnectClient(skipInitialDelay = true)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Activity: onDestroy")
        super.onDestroy()

        if (this::webRtcClient.isInitialized) {
            Log.d(TAG, "Disposing client...")
            webRtcClient.dispose()
        }

        //dispose coroutine scope
        scope.cancel()
    }
    //endregion

    //region Permissions
    /**
     * On request permissions result callback.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                Log.d(TAG, "Received permissions request results")

                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d(TAG, "All permissions have been granted")

                    initialize()
                } else {
                    Log.e(TAG, "Some permissions haven't been granted")
                    finishAffinity()
                }

                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    /**
     * Checks and, if necessary, requests permissions.
     * @return True, if all permission have already been granted; otherwise false
     */
    private fun checkPermissions(): Boolean {

        Log.d(TAG, "Checking permissions...")

        try {
            val pi = packageManager.getPackageInfo(applicationContext.packageName, PackageManager.GET_PERMISSIONS)
            val notGranted = pi.requestedPermissions?.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }!!

            //show rationale if needed
            if (notGranted.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.permissions_rationale_title))
                    .setMessage(R.string.permissions_rationale_message)
                    .show()
            }

            if (notGranted.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, notGranted.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
            } else {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check or request permissions", e)
        }

        return false
    }

    //endregion

    //region helpers
    /**
     * Reads the configuration from the resources.
     */
    private fun readConfig() {
        Log.d(TAG, "Reading configuration...")
        val resources: Resources = applicationContext.resources

        try {
            val gson = Gson()
            val rawResource = resources.openRawResource(R.raw.config)
            this.config = gson.fromJson(rawResource.bufferedReader(), DeviceConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to retrieve configuration from file.", e)
            throw IllegalStateException("Application cannot run without a proper configuration")
        }
    }

    /**
     * Initializes all main resources.
     */
    private fun initialize() {
        Log.d(TAG, "Enumerating camera devices...")

        changeLoadingVisibility(true)
        changeMessageStatus("Initializing camera...")

        //prints all supported formats to log
        val enumerator = Camera2Enumerator(application)

        val devices = enumerator.deviceNames.map { it }
        if (devices.isNotEmpty()) {
            Log.d(TAG, "Available devices: ${devices.joinToString(", ")}")

            val selectedDevice = config.camera.device ?: devices.first()
            Log.d(TAG, "Selected device: $selectedDevice")
            initClient(selectedDevice)
        } else {
            Log.e(TAG, "No camera device found")

            scope.launch(Dispatchers.Main) {
                showClosingDialog(this@MainActivity,
                    getString(R.string.alert_title_camera_not_found),
                    getString(R.string.alert_message_camera_not_found))
                finishAffinity()
            }
        }
    }

    /**
     * Initializes the WebRtcClient.
     * @param cameraDevice The camera device id (default: none)
     */
    private fun initClient(cameraDevice: String? = null) {
        Log.d(TAG, "Initializing WebRTC client...")

        changeMessageStatus(getString(R.string.status_message_initializing_client))

        val iceServer = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val options = WebRtcOptions(config.peerjs, iceServer)

        val mediaOptions = MediaOptions(
            enableAudio = true,
            enableVideo = true,
            videoCaptureDeviceName = cameraDevice,
            videoCaptureFormat = config.camera.format
        )

        //create client
        webRtcClient = WebRtcPeer(application, options, this).apply {
            initLocalMedia(mediaOptions)
        }
    }

    /**
     * Subscribe the operator to the backend.
     * @param peerId The peer id
     */
    private suspend fun subscribeOperator(peerId: String) {

        val baseUrl = config.backend.buildUrl()

        HttpClient {
            install(ContentNegotiation) {
                gson {
                    serializeNulls()
                    disableHtmlEscaping()
                }
            }
        }.use {
            try {
                //execute login
                val loginResponse = it.post {
                    url("$baseUrl$LOGIN_URL")
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(config.credentials.username, config.credentials.password))
                }.body<LoginResponse>()
                Log.d(TAG, "Logged into system")

                //subscribe peer
                it.post {
                    url("$baseUrl$ADD_PEER_URL")
                    header(AUTHORIZATION_HEADER_KEY, String.format(JWT_FORMAT, loginResponse.token))
                    contentType(ContentType.Application.Json)
                    setBody(AddPeerRequest(peerId, loginResponse.user.id, config.peerInfo.description))
                }
                Log.d(TAG, "Added this device as available peer")

            } catch (e: Throwable) {
                Log.e(TAG, "Unable to subscribe the operator", e)

                if (!isBusy()) {
                    withContext(Dispatchers.Main) {
                        showClosingDialog(this@MainActivity,
                            getString(R.string.alert_title_server_unavailable),
                            getString(R.string.alert_message_server_unavailable))
                        finishAffinity()
                    }
                } else {
                    Log.d(TAG, "Skipped application closing on subscription or login error because one or more connection are still active")
                }
            }
        }
    }

    /**
     * Manages media connection.
     * @param connection The media connection.
     */
    private fun manageMediaConnection(connection: MediaConnection) = scope.launch(Dispatchers.Default) {
        for (track in connection.awaitVideoTracks()) {
            Log.i(TAG, "Received new remote video track: $track")
        }
    }

    /**
     * Manages data connection.
     * @param connection The data connection.
     */
    private fun manageDataConnection(connection: DataConnection) = scope.launch(Dispatchers.Default) {
        for (channel in connection.awaitExchanges()) {
            Log.i(TAG, "Received a new data channel (${channel.id}")

            val gson = Gson()
            try {
                launch(Dispatchers.Main) {

                    for (data in channel.receive()) {
                        try {
                            val messageBody = gson.fromJson(data, MessageBody::class.java)

                            when(messageBody.type) {
                                MessageType.Started -> {
                                    showSnackBar("Session started")
                                }
                                MessageType.NextStep -> {
                                    val stepInfo = gson.fromJson(messageBody.data, StepInfo::class.java)
                                    val message = String.format("%s\n%s", stepInfo.name, stepInfo.description)
                                    changeStepDescription(message)
                                }
                                MessageType.Finished -> {
                                    showSnackBar("Session completed")
                                }
                                MessageType.Aborted -> {
                                    showSnackBar("Session aborted")
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Unable to parse incoming message", e)
                        }
                    }

                }
            } catch (e: Throwable) {
                Log.e(TAG, "Channel closed: ${channel.id}")
            }
        }
    }

    /**
     * Reconnects the client.
     * @param attempt The attempt number
     * @param skipInitialDelay Whether to skip the initial delay or not.
     */
    private suspend fun reconnectClient(attempt: Int = 1, skipInitialDelay: Boolean = false) {

        if (attempt == 1) {
            if ( this.reconnecting) {
                Log.d(TAG, "Skipped reconnection, another one is still running")
                return
            } else {
                this.reconnecting = true
            }

            if (!skipInitialDelay) {
                Log.d(TAG, "Waiting initial delay before starting reconnection")
                delay(DEFAULT_INITIAL_RECONNECTION_DELAY)
            }
        }

        if (attempt <= DEFAULT_MAX_RECONNECTION_ATTEMPTS) {
            try {
                if (webRtcClient.connected()) {
                    Log.d(TAG, "Skipping reconnection attempt #$attempt, the client is already connected")
                    this.reconnecting = false
                    return
                }

                Log.d(TAG, "Attempting reconnection: #$attempt")

                changeMessageStatus(getString(R.string.message_status_connect_to_server))
                val peerId = webRtcClient.connect(config.peerInfo.peerId)
                changeMessageStatus(getString(R.string.message_status_subscribing_device))
                subscribeOperator(peerId)
                changeMessageStatus(getString(R.string.message_status_wait_incoming_request))

                this.reconnecting = false

            } catch (e: Throwable) {
                Log.e(TAG,"Reconnection attempt #$attempt: failed")

                val delay = DEFAULT_RECONNECTION_DELAY * attempt
                delay(delay)
                reconnectClient(attempt + 1)
            }
        } else {
            Log.e(TAG,"Unable to reconnect to the peerjs server")
            this.reconnecting = false

            //avoid close the application if some connection are still active
            if (!isBusy()) {
                withContext(Dispatchers.Main) {
                    showClosingDialog(this@MainActivity,
                        getString(R.string.alert_title_peerjs_server_max_attempts_reached),
                        getString(R.string.alert_message_peerjs_server_max_attempts_reached))
                    finishAffinity()
                }
            }
        }
    }

    /**
     * Determines whether a rtc connection of the specified type is already present.
     * @param type The Rtc connection type.
     * @return True, if an rtc connection of the same type is present.
     */
    private fun isBusy(type: RtcConnectionType? = null): Boolean {
        return if (type != null) {
            connections.any { it.getType() == type }
        } else {
            connections.size > 0
        }
    }
    //endregion

    //region interface helpers
    /**
     * Changes the message show in the loading status textview.
     * @param message The message to show.
     */
    private fun changeMessageStatus(message: String) {
        binding.messageStatus.text = message
    }

    /**
     * Changes the loading progress bar visibility.
     * @param visible Whether the loading progress bar should be visible or not.
     */
    private fun changeLoadingVisibility(visible: Boolean) {
        binding.initializationLayout.isGone = !visible
        binding.callLayout.isGone = visible
        binding.stepDescription.text = ""
    }

    /**
     * Show a dialog.
     * @param context The context
     * @param title The title
     * @param message The message
     * @return The dialog
     */
    private fun showDialog(context: Context, title: String, message: String): AlertDialog {
        val alert = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .create()
        alert.show()
        return alert
    }

    /**
     * Show a dialog and close it after the specified amount of time.
     * @param context The context
     * @param title The title
     * @param message The message
     * @param timeout The amount of milliseconds after which the dialog will be closed
     */
    private suspend fun showClosingDialog(context: Context, title: String, message: String, timeout: Long = 5000) {
        val alert = showDialog(context, title, message)
        delay(timeout)
        alert.cancel()
    }

    /**
     * Changes the step description.
     * @param description The step description
     */
    private fun changeStepDescription(description: String) {
        binding.stepDescription.text = description
    }

    /**
     * Shows a snackbar.
     * @param message The message to show
     * @param length The snackbar length (default: short)
     */
    private fun showSnackBar(message: String, length: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(findViewById(android.R.id.content), message, length).show()
    }
    //endregion

    //region webrtceventlistener
    override fun onSignallingConnectionOpened() {
        Log.d(TAG, "Connection with the signalling server has been opened")
    }

    override fun onSignallingConnectionError(error: String) {
        Log.d(TAG, "Connection error with signalling server: $error")
    }

    override fun onSignallingConnectionClosed() {
        Log.d(TAG, "Connection closed with signalling server")

        scope.launch(Dispatchers.Main) {
            if (!isBusy()) {
                changeMessageStatus(getString(R.string.message_status_connect_to_server))
                changeLoadingVisibility(true)
            }

            reconnectClient()
        }
    }

    override fun onCallRequest(remotePeerId: String, connection: MediaConnection, continuation: Continuation<Boolean>) {
        Log.d(TAG, "Received a call request from: $remotePeerId")

        val busy = isBusy(connection.getType())
        if (!busy) {
            Log.d(TAG, "Start waiting for streams...")
            connections.add(connection)
            manageMediaConnection(connection)

            scope.launch(Dispatchers.Main) {
                changeLoadingVisibility(false)
            }
        }

        continuation.resume(!busy)
    }

    override fun onDataExchangeRequest(remotePeerId: String, connection: DataConnection, continuation: Continuation<Boolean>) {
        Log.d(TAG, "Received a new data exchange request from: $remotePeerId")

        val busy = isBusy(connection.getType())
        if (!busy) {
            Log.d(TAG, "Start waiting for incoming data...")
            connections.add(connection)
            manageDataConnection(connection)

            scope.launch(Dispatchers.Main) {
                changeLoadingVisibility(false)
            }
        }

        continuation.resume(!busy)
    }

    override fun onConnectionClosed(connection: Connection) {
        Log.d(TAG, "A Connection has been closed: ${connection.getConnectionId()}")

        connections.removeAll { it.getConnectionId() == connection.getConnectionId() }

        //change ui if no connection is active
        if (connections.isEmpty()) {
            scope.launch(Dispatchers.Main) {
                changeMessageStatus(getString(R.string.message_status_wait_incoming_request))
                changeLoadingVisibility(true)

            }
        }

        showSnackBar("Connection ended with '${connection.getRemotePeerId()}'")
    }
    //endregion
}
