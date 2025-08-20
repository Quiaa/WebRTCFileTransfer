package com.example.webrtcfiletransfer.data.model

// Represents a user in the application.
// We use default values to facilitate Firestore's toObject() mapping.
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = ""
)
