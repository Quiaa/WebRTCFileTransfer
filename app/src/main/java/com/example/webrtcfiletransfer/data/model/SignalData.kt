package com.example.webrtcfiletransfer.data.model

// Data class to represent signaling messages.
// All properties must have default values for Firestore's toObject() method to work correctly.
data class SignalData(
    val type: String = "",
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)
