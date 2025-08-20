package com.example.webrtcfiletransfer.data.model

// A data class to bundle the filename and its content for transfer.
// The file content is Base64 encoded to be sent as a string.
data class FileTransferData(
    val filename: String,
    val data: String // Base64 encoded file content
)
