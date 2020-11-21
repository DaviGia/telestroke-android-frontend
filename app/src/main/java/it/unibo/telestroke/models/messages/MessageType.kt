package it.unibo.telestroke.models.messages

import com.google.gson.annotations.SerializedName

enum class MessageType {
    @SerializedName("0")
    Started,
    @SerializedName("1")
    NextStep,
    @SerializedName("2")
    Finished,
    @SerializedName("3")
    Aborted
}