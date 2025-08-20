package com.example.webrtcfiletransfer

import android.app.Application
import com.example.webrtcfiletransfer.data.repository.TransferRepository

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize global components here.
        // This ensures the signal listener is started once and for the entire app lifecycle.
        TransferRepository.startListening()
    }
}
