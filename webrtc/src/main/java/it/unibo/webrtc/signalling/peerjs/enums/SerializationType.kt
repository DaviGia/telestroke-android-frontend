package it.unibo.webrtc.signalling.peerjs.enums

import com.google.gson.annotations.SerializedName

enum class SerializationType {
    @SerializedName("binary")
    Binary,
    @SerializedName("binary-utf8")
    BinaryUTF8,
    @SerializedName("json")
    JSON
}