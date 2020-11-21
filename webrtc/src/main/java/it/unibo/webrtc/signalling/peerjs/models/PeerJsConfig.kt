package it.unibo.webrtc.signalling.peerjs.models

/**
 * PeerJs configuration.
 */
data class PeerJsConfig(val host: String, val port: Int, val apiUrl: String, val apiKey: String, val secure: Boolean = true) {

    /**
     * Builds the complete url to the api.
     * @return The complete url.
     */
    fun buildUrl(): String = "http${if (secure) "s" else ""}://${host}:${port}/${apiUrl.trimEnd('/')}"
}

