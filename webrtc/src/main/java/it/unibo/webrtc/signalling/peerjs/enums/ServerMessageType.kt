package it.unibo.webrtc.signalling.peerjs.enums

import com.google.gson.annotations.SerializedName

enum class ServerMessageType {
    @SerializedName("HEARTBEAT")
    Heartbeat,
    @SerializedName("CANDIDATE")
    Candidate,
    @SerializedName("OFFER")
    Offer,
    @SerializedName("ANSWER")
    Answer,
    @SerializedName("OPEN")
    Open, // The connection to the server is open.
    @SerializedName("ERROR")
    Error, // Server error.
    @SerializedName("ID-TAKEN")
    IdTaken, // The selected ID is taken.
    @SerializedName("INVALID-KEY")
    InvalidKey, // The given API key cannot be found.
    @SerializedName("LEAVE")
    Leave, // Another peer has closed its connection to this peer.
    @SerializedName("EXPIRE")
    Expire // The offer sent to a peer has expired without response.
}