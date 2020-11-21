package it.unibo.telestroke.models.response

import it.unibo.telestroke.models.UserDetails

data class LoginResponse(val user: UserDetails,
                         val token: String,
                         val refreshToken: String)