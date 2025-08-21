package com.example.webrtcfiletransfer.data.model

import android.os.Parcelable
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize
import java.util.*

@IgnoreExtraProperties
@Parcelize
data class CallSession(
    // We don't store the ID in the document itself, but it's useful in the client
    @get:com.google.firebase.firestore.Exclude
    var callId: String? = null,

    val callerId: String? = null,
    val calleeId: String? = null,
    var offer: Sdp? = null,
    var answer: Sdp? = null,
    var status: String? = null,
    val fileMetaData: FileMetaData? = null,

    @ServerTimestamp
    val createdAt: Date? = null
) : Parcelable {
    // Add a no-argument constructor for Firestore deserialization
    constructor() : this(null, null, null, null, null, null, null, null)
}

@IgnoreExtraProperties
@Parcelize
data class Sdp(
    val type: String? = null,
    val description: String? = null
) : Parcelable {
    // Add a no-argument constructor for Firestore deserialization
    constructor() : this(null, null)
}

@IgnoreExtraProperties
@Parcelize
data class IceCandidateModel(
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = -1
) : Parcelable {
    constructor() : this(null, null, -1)
}
