package com.example.webrtcfiletransfer.data.model

import android.os.Parcelable
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@IgnoreExtraProperties
@Parcelize
data class FileMetaData(
    val filename: String? = null,
    val size: Long? = null
) : Parcelable {
    constructor() : this(null, null)
}
