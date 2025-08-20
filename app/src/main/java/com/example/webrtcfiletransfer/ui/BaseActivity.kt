package com.example.webrtcfiletransfer.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcfiletransfer.ui.transfer.TransferRequestDialogFragment
import com.example.webrtcfiletransfer.viewmodel.MainViewModel

abstract class BaseActivity : AppCompatActivity() {

    // Get a reference to the shared MainViewModel
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeTransferRequests()
    }

    private fun observeTransferRequests() {
        mainViewModel.incomingAppEvent.observe(this) { event ->
            event?.let { (senderId, signal) ->
                if (signal.type == "TRANSFER_REQUEST") {
                    // To prevent showing the dialog multiple times, check if it's already shown
                    if (supportFragmentManager.findFragmentByTag(TransferRequestDialogFragment.TAG) == null) {
                        val dialog = TransferRequestDialogFragment.newInstance(senderId, signal)
                        dialog.show(supportFragmentManager, TransferRequestDialogFragment.TAG)
                    }
                }
                // Consume the event to prevent it from being shown again on config change
                mainViewModel.consumeAppEvent()
            }
        }
    }
}
