package it.unibo.webrtc.signalling.peerjs.models

import android.util.Log
import com.google.gson.JsonObject
import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType
import org.webrtc.IceCandidate

/**
 * The CANDIDATE message from PeerJS server.
 */
data class Candidate(val candidate: JsonObject,
                     val type: ConnectionType,
                     val connectionId: String) {

    companion object {
        private val TAG = Candidate::class.java.simpleName

        private const val CANDIDATE_SDP_M_ID_KEY = "sdpMid"
        private const val CANDIDATE_SDP_M_LINE_INDEX_KEY = "sdpMLineIndex"
        private const val CANDIDATE_KEY = "candidate"

        /**
         * Builds a candidate.
         * @param iceCandidate The ICE candidate
         * @param type The connection type
         * @param connectionId The connection id
         * @return The candidate
         */
        fun buildCandidate(iceCandidate: IceCandidate, type: ConnectionType, connectionId: String): Candidate {
            val candidate = JsonObject()
            candidate.addProperty(CANDIDATE_SDP_M_ID_KEY, iceCandidate.sdpMid)
            candidate.addProperty(CANDIDATE_SDP_M_LINE_INDEX_KEY, iceCandidate.sdpMLineIndex)
            candidate.addProperty(CANDIDATE_KEY, iceCandidate.sdp)
            return Candidate(candidate, type, connectionId)
        }
    }

    /**
     * Gets the ICE candidate.
     * @return The ICE candidate
     */
    fun getCandidate(): IceCandidate? {
        try {
            val sdpMid = candidate.get(CANDIDATE_SDP_M_ID_KEY)?.asString
            val sdpMLineIndex = candidate.get(CANDIDATE_SDP_M_LINE_INDEX_KEY)?.asInt
            val candidate = candidate.get(CANDIDATE_KEY)?.asString

            candidate?.let { c ->
                sdpMid?.let { id ->
                    sdpMLineIndex?.let { idx ->
                        return IceCandidate(id, idx, c)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unable to build IceCandidate from candidate", e)
        }
        return null
    }
}