package it.unibo.webrtc.signalling.peerjs.models

import com.google.gson.JsonObject
import it.unibo.webrtc.signalling.peerjs.enums.ServerMessageType

data class ServerMessage(val type: ServerMessageType,
                         val payload: JsonObject? = null,
                         val src: String? = null,
                         val dst: String? = null)