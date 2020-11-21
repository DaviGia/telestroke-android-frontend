package it.unibo.telestroke.models

data class BackendConfig(val host: String, val port: Int, val baseUrl: String, val secure: Boolean) {

    /**
     * Builds the complete url to the api.
     * @return The complete url.
     */
    fun buildUrl(): String = "http${if (secure) "s" else ""}://${host}:${port}/${baseUrl.trimEnd('/')}"
}