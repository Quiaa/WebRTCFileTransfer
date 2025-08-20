package com.example.webrtcfiletransfer.data.model

import com.example.webrtcfiletransfer.data.model.FileMetaData

// Data class to represent signaling messages.
// All properties must have default values for Firestore's toObject() method to work correctly.
data class SignalData(
    // Types can be: OFFER, ANSWER, ICE_CANDIDATE, TRANSFER_REQUEST, TRANSFER_ACCEPT, TRANSFER_REJECT
    val type: String = "",
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val fileMetaData: FileMetaData? = null
)
