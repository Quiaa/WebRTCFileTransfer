package com.example.webrtcfiletransfer.ble

sealed class VerificationResult {
    object InProgress : VerificationResult()
    data class Success(val uid: String) : VerificationResult()
    data class Failure(val message: String) : VerificationResult()
}
