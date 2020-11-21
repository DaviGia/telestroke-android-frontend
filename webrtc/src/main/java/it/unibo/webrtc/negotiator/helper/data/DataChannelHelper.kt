package it.unibo.webrtc.negotiator.helper.data

import android.util.Log
import it.unibo.webrtc.common.Disposable
import it.unibo.webrtc.connection.models.ExchangeDataChannel
import it.unibo.webrtc.negotiator.helper.data.observer.DataChannelEventListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import java.util.concurrent.CancellationException

/**
 * The data channel helper.
 * @param channel The data channel
 * @param listener The listener for data channel events
 */
class DataChannelHelper(private val channel: DataChannel, private val listener: DataChannelEventListener)
    : DataChannel.Observer, Disposable {

    companion object {
        private val TAG = DataChannelHelper::class.java.simpleName
    }

    //region fields
    private var job: Job? = null

    /**
     * The channel with the outgoing messages.
     */
    private val outgoing: Channel<String> = Channel(Channel.UNLIMITED)

    /**
     * The channel with the incoming messages.
     */
    private val incoming: Channel<String> = Channel(Channel.UNLIMITED)
    //endregion

    init {
        Log.d(TAG, "Initializing data channel helper for channel: ${channel.id()}")
        channel.registerObserver(this)
    }

    //region getters
    /**
     * Gets the underlying channel id.
     * @return The channel id
     */
    fun channelId(): Int = channel.id()

    /**
     * Gets the underlying channel label.
     * @return The channel label
     */
    fun channelLabel(): String = channel.label()
    //endregion

    //region observer
    override fun onMessage(buffer: DataChannel.Buffer?) {
        buffer?.let {
            try {
                if (it.binary) {
                    throw IllegalArgumentException("Binary serialization is not supported")
                }

                val data = Charsets.UTF_8.decode(it.data).toString()

                Log.d(TAG, "Received data from channel (${channel.id()}): $data")

                if (incoming.offer(data)) {
                    Log.v(TAG, "Successfully offered received message to the send channel")
                } else {
                    Log.v(TAG, "The received message couldn't be offered to the send channel")
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Unable to handle incoming message", e)
            }
        } ?: Log.e(TAG, "Received empty buffer")
    }

    override fun onBufferedAmountChange(amount: Long) {
        Log.d(TAG, "Buffered mount changed: $amount")
    }

    override fun onStateChange() {
        channel.state()?.let {
            Log.d(TAG, "Data channel state changed to: $it")

            when(it) {
                DataChannel.State.OPEN -> {
                    //wait incoming messages and deliver them
                    job = GlobalScope.launch {
                        try {
                            for (message in outgoing) {
                                handleMessage(message)
                            }
                        } catch (e: CancellationException) {
                            Log.d(TAG, "Outgoing channel closed (reason: ${e.message})")
                        } catch (e: Throwable) {
                            Log.e(TAG, "Detected error for outgoing channel", e)
                        }
                    }
                    listener.onChannelOpened(ExchangeDataChannel(channel.id(), incoming, outgoing) )
                }
                DataChannel.State.CLOSED -> {
                    Log.d(TAG, "Detected data channel shutdown, removing from active channels...")
                    listener.onChannelClosed(channel.id())
                    channel.unregisterObserver()
                }
                else -> {}
            }
        }
    }
    //endregion

    //region helpers
    override fun dispose() {
        val cause = CancellationException("Data channel helper was disposed")
        incoming.close(cause)
        outgoing.cancel(cause)
        job?.cancel(cause)

        channel.close()
        channel.dispose()
    }

    private fun handleMessage(message: String) {
        try {
            val encoded = Charsets.UTF_8.encode(message)
            val buffer = DataChannel.Buffer(encoded, false)
            channel.send(buffer)
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to process incoming message from receive channel", e)
        }
    }
    //endregion
}