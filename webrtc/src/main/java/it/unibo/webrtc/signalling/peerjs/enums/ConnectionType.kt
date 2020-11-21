package it.unibo.webrtc.signalling.peerjs.enums

import com.google.gson.annotations.SerializedName

enum class ConnectionType {
    @SerializedName("data")
    Data,
    @SerializedName("media")
    Media
}