package it.unibo.webrtc.signalling.peerjs.util

import kotlin.random.Random

private val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

/**
 * Generates a random string.
 * @param length The length of the string to generate (default: 11)
 * @return The generated string
 */
fun randomToken(length: Int = 11): String {
    return (1..length)
        .map { Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("")
}