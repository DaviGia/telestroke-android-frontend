package it.unibo.webrtc.signalling.peerjs.models

import com.google.gson.JsonObject
import it.unibo.webrtc.signalling.peerjs.enums.SerializationType

/**
 * Model that represents additional data offer options.
 * @param label The label
 * @param metadata The metadata
 * @param serialization The serialization type
 * @param reliable The channel reliability
 */
data class DataOfferOptions(val label: String? = null,
                            val metadata: JsonObject? = null,
                            val serialization: SerializationType = SerializationType.JSON,
                            val reliable: Boolean? = null)