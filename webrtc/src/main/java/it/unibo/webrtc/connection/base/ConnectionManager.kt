package it.unibo.webrtc.connection.base

import org.webrtc.MediaConstraints
import org.webrtc.SessionDescription

/**
 * RTC Connection manager interface.
 *
 * Presents an object that handles the main action for a WebRTC Media Connection.
 */
interface ConnectionManager {

    /**
     * Sets the connectionId.
     * @param connectionId The connection id
     */
    fun setConnectionId(connectionId: String)

    /**
     * Creates an offer.
     * @param mediaConstraints The media constraints
     * @return The SDP of the remote peer
     */
    suspend fun createOffer(mediaConstraints: MediaConstraints = MediaConstraints()): SessionDescription?

    /**
     * Answers to a remote peer offer.
     * @param offerDescription The offer session description
     * @param mediaConstraints The media constraints
     * @return The SDP of the remote peer
     */
    suspend fun answer(offerDescription: SessionDescription, mediaConstraints: MediaConstraints = MediaConstraints()): SessionDescription?

    /**
     * Sets the SDP description of a remote peer answering to a call.
     * @param answerDescription The answer session description
     */
    suspend fun receiveAnswer(answerDescription: SessionDescription)
}