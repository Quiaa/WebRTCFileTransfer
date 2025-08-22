package com.example.webrtcfiletransfer.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcfiletransfer.ui.transfer.TransferRequestDialogFragment
import com.example.webrtcfiletransfer.viewmodel.MainViewModel

abstract class BaseActivity : AppCompatActivity() {

    // Get a reference to the shared MainViewModel
    abstract val mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeTransferRequests()
    }

    private fun observeTransferRequests() {
        mainViewModel.incomingCallEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { session ->
                // To prevent showing the dialog multiple times, check if it's already shown
                if (supportFragmentManager.findFragmentByTag(TransferRequestDialogFragment.TAG) == null) {
                    val dialog = TransferRequestDialogFragment.newInstance(session)
                    dialog.show(supportFragmentManager, TransferRequestDialogFragment.TAG)
                }
            }
        }
    }
}
