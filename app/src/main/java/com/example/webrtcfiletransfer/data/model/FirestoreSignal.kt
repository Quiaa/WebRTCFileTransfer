package com.example.webrtcfiletransfer.data.model

// A helper data class that matches the structure of the document in Firestore.
// This allows us to use Firestore's built-in toObject() method for safe deserialization.
// It must have a no-argument constructor, so all properties need default values.
data class FirestoreSignal(
    val sender: String = "",
    val signal: SignalData = SignalData()
)
