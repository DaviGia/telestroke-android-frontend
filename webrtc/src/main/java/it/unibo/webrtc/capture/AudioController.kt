package it.unibo.webrtc.capture

/**
 * Audio controller.
 */
interface AudioController {

    /**
     * Sets the volume.
     * @param volume The volume
     */
    fun setVolume(volume: Double)

    /**
     * Mutes the audio.
     */
    fun mute()

    /**
     * Unmutes the audio.
     */
    fun unmute()
}