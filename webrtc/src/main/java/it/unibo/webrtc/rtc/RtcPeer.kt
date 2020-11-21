package it.unibo.webrtc.rtc

import android.content.Context
import android.util.Log
import it.unibo.webrtc.capture.AudioController
import it.unibo.webrtc.capture.CameraCapturer
import it.unibo.webrtc.capture.CameraController
import it.unibo.webrtc.capture.models.MediaOptions
import org.webrtc.*

/**
 * RTC peer.
 *
 * Manages the local MediaStream and creates PeerConnections.
 *
 * @param context The application context
 */
class RtcPeer(private val context: Context) : RtcClient, AudioController {

    companion object {
        private val TAG = RtcPeer::class.java.simpleName

        private const val LOCAL_VIDEO_TRACK_ID = "local_video_track"
        private const val LOCAL_AUDIO_TRACK_ID = "local_audio_track"
        private const val LOCAL_STREAM_ID = "local"

        private const val MUTE_VOLUME_VALUE = 0.0
    }

    //region fields
    /**
     * The peer connection factory
     */
    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }

    /**
     * The OpenGL surface used to render camera frames.
     */
    private val rootEglBase: EglBase = EglBase.create()
    /**
     * The local stream video capturer.
     */
    private var capturer: CameraCapturer? = null
    /**
     * The current local stream.
     */
    private var localStream: MediaStream? = null
    //endregion

    init {
        initPeerConnectionFactory(context)
    }

    //region Initialization
    /**
     * Initializes the peer connection factory options.
     * @param context The application context
     */
    private fun initPeerConnectionFactory(context: Context) {
        Log.d(TAG, "Initializing PeerConnectionFactory options...")

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true) //print stack traces of internal exceptions
            .setInjectableLogger({ message, severity, tag -> //declare a logger to not use the default one
                when(severity) {
                    Logging.Severity.LS_VERBOSE -> Log.v(tag, message)
                    Logging.Severity.LS_INFO -> Log.i(tag, message)
                    Logging.Severity.LS_WARNING -> Log.w(tag, message)
                    Logging.Severity.LS_ERROR -> Log.e(tag, message)
                    else -> {}
                }
            }, Logging.Severity.LS_WARNING)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)
    }

    /**
     * Builds the peer connection factory.
     */
    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        Log.d(TAG, "Building PeerConnectionFactory...")

        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }
    //endregion

    //region actions
    override fun initLocalStream(options: MediaOptions) {
        Log.d(TAG, "Initializing local stream...")

        capturer?.let {
            throw UnsupportedOperationException("Capture device has already been initialized")
        } ?: run {
            capturer = CameraCapturer(
                context,
                rootEglBase.eglBaseContext,
                options.videoCaptureDeviceName
            )

            //build local stream
            localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)

            //create source and tracks
            val audioTracks: MutableList<AudioTrack> = mutableListOf()
            val videoTracks: MutableList<VideoTrack> = mutableListOf()

            if (options.enableVideo) {
                val videoSource = peerConnectionFactory.createVideoSource(false)
                val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_VIDEO_TRACK_ID, videoSource)
                videoTracks.add(localVideoTrack)

                //start capturing from camera device
                capturer!!.startCapture(videoSource, options.videoCaptureFormat)
            }

            if (options.enableAudio) {
                val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
                val localAudioTrack = peerConnectionFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource)

                //mute local audio playback
                localAudioTrack.setVolume(MUTE_VOLUME_VALUE)

                audioTracks.add(localAudioTrack)
            }

            //add tracks to local stream
            audioTracks.forEach { localStream?.addTrack(it) }
            videoTracks.forEach { localStream?.addTrack(it) }
        }
    }

    override fun getLocalStream(): MediaStream? = localStream

    override fun initRenderer(renderer: SurfaceViewRenderer, isMirrored: Boolean) = renderer.run {
        Log.d(TAG, "Initializing surface view renderer")
        setMirror(isMirrored)
        setEnableHardwareScaler(true)
        release() //release before initializing it
        init(rootEglBase.eglBaseContext, object: RendererCommon.RendererEvents {
            override fun onFrameResolutionChanged(width: Int, height: Int, framerRate: Int) {
                Log.d(TAG, "Frame resolution changed to ${width}x${height}@${framerRate}")
            }
            override fun onFirstFrameRendered() {
                Log.d(TAG, "First frame rendered")
            }
        })
    }

    override fun createConnection(configuration: PeerConnection.RTCConfiguration, connectionObserver: PeerConnection.Observer): PeerConnection {
        Log.d(TAG, "Building a new peer connection...")

        return peerConnectionFactory.createPeerConnection(configuration, connectionObserver)
            ?: throw IllegalStateException("Unable to create peer connection")
    }

    override fun getCameraController(): CameraController = capturer?.let{ it } ?: throw UnsupportedOperationException("Camera capturer hasn't been initialized")

    override fun getAudioController(): AudioController = this

    override fun dispose() {
        capturer?.dispose()
        localStream?.dispose()
        peerConnectionFactory.dispose()
        rootEglBase.release()
    }
    //endregion

    //region audio controller
    override fun setVolume(volume: Double) {
        localStream?.audioTracks?.forEach { it.setVolume(volume) }
    }

    override fun mute() {
        localStream?.audioTracks?.forEach { it.setEnabled(false) }
    }

    override fun unmute() {
        localStream?.audioTracks?.forEach { it.setEnabled(true) }
    }
    //endregion
}