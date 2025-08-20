package com.example.webrtcfiletransfer.util

// A generic class that holds a value with its loading status.
// This is used to communicate states between the Repository/ViewModel and the UI.
sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    // Represents a successful state with data.
    class Success<T>(data: T) : Resource<T>(data)
    // Represents an error state with a message.
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    // Represents the loading state.
    class Loading<T> : Resource<T>()
}
