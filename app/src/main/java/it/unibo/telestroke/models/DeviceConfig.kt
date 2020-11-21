package it.unibo.telestroke.models

import it.unibo.webrtc.signalling.peerjs.models.PeerJsConfig

data class DeviceConfig(val peerjs: PeerJsConfig,
                        val backend: BackendConfig,
                        val credentials: UserCredentials,
                        val peerInfo: PeerInfo,
                        val camera: CameraConfig)