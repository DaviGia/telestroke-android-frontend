package it.unibo.webrtc.signalling.peerjs.models

import it.unibo.webrtc.signalling.peerjs.enums.ConnectionType

/**
 * The PeerJS connection information.
 * @param id The identifier
 * @param type The connection type
 * @param peerId The remote peer id
 */
data class ConnectionInfo(val id: String, val type: ConnectionType, val peerId: String)