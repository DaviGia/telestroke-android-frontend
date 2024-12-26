package it.unibo.webrtc.signalling.peerjs.models

import android.util.Log
import com.google.gson.JsonObject
import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType
import org.webrtc.SessionDescription
import java.util.*

/**
 * The OFFER message from PeerJS server.
 */
data class Offer(val sdp: JsonObject,
                 val type: ConnectionType,
                 val connectionId: String,
                 val browser: String = "android-native") {

    companion object {
        private val TAG = Offer::class.java.simpleName

        private const val OFFER_TYPE_KEY = "type"
        private const val OFFER_DESCRIPTION_KEY = "sdp"

        /**
         * Builds an offer.
         * @param sessionDescription The session description
         * @param type The connection type
         * @param connectionId The connection id
         * @return The offer
         */
        fun buildOffer(sessionDescription: SessionDescription, type: ConnectionType, connectionId: String): Offer {
            val sdp = JsonObject()
            sdp.addProperty(OFFER_TYPE_KEY, sessionDescription.type.name.lowercase(Locale.getDefault()))
            sdp.addProperty(OFFER_DESCRIPTION_KEY, sessionDescription.description)
            return Offer(sdp, type, connectionId)
        }
    }

    /**
     * Gets the session description.
     * @return The session description
     */
    fun getSessionDescription(): SessionDescription? {
        try {
            val type = sdp.get(OFFER_TYPE_KEY)?.asString
            val description = sdp.get(OFFER_DESCRIPTION_KEY)?.asString

            type?.let { t ->
                description?.let { d ->
                    val sessionType = SessionDescription.Type.fromCanonicalForm(t)
                    return SessionDescription(sessionType, d)
                }
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Unable to build SessionDescription from offer", e)
        }
        return null
    }
}