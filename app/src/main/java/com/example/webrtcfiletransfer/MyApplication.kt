package com.example.webrtcfiletransfer

import android.app.Application
import com.example.webrtcfiletransfer.data.repository.TransferRepository

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global initialization can go here if needed in the future.
        // The TransferRepository listener is now started by the MainViewModel.
    }
}
