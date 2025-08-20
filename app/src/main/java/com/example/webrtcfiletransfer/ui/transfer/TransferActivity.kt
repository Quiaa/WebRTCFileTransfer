package com.example.webrtcfiletransfer.ui.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.databinding.ActivityTransferBinding
import com.example.webrtcfiletransfer.util.WebRTCClient
import com.example.webrtcfiletransfer.util.WebRTCListener
import com.example.webrtcfiletransfer.viewmodel.TransferViewModel
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.io.IOException
import java.nio.ByteBuffer

class TransferActivity : AppCompatActivity(), WebRTCListener {

    private val TAG = "TransferActivity"
    private lateinit var binding: ActivityTransferBinding
    private val viewModel: TransferViewModel by viewModels()

    private lateinit var webRTCClient: WebRTCClient
    private var targetUserId: String? = null
    private var targetUsername: String? = null
    private var isCaller = false
    private var selectedFileUri: Uri? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                binding.btnSendFile.isEnabled = true
                Toast.makeText(this, "File selected: ${uri.path}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getStringExtra("target_user_id")
        targetUsername = intent.getStringExtra("target_username")
        isCaller = intent.getBooleanExtra("is_caller", true)

        if (targetUserId == null || targetUsername == null) {
            finish()
            return
        }

        binding.tvTargetUser.text = "Transfer with: $targetUsername"

        webRTCClient = WebRTCClient(application, this)

        setupClickListeners()
        setupObservers()

        if (isCaller) {
            webRTCClient.createOffer()
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectFile.setOnClickListener {
            openFilePicker()
        }
        binding.btnSendFile.setOnClickListener {
            sendFile()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        filePickerLauncher.launch(intent)
    }

    private fun sendFile() {
        selectedFileUri?.let { uri ->
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes != null) {
                    webRTCClient.sendFile(ByteBuffer.wrap(fileBytes))
                    Toast.makeText(this, "Sending file...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.signalData.observe(this) { (sender, signal) ->
            Log.d(TAG, "Received signal: ${signal.type} from $sender")
            when (signal.type) {
                "OFFER" -> {
                    signal.sdp?.let {
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, it)
                        webRTCClient.onRemoteSessionReceived(sdp)
                        webRTCClient.createAnswer()
                    }
                }
                "ANSWER" -> {
                    signal.sdp?.let {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, it)
                        webRTCClient.onRemoteSessionReceived(sdp)
                    }
                }
                "ICE_CANDIDATE" -> {
                    if (signal.sdp != null && signal.sdpMid != null && signal.sdpMLineIndex != null) {
                        val candidate = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.sdp)
                        webRTCClient.addIceCandidate(candidate)
                    }
                }
            }
        }

        viewModel.signalError.observe(this) { error ->
            Toast.makeText(this, "Signaling error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        runOnUiThread {
            binding.tvConnectionStatus.text = "Status: ${state.name}"
        }
    }

    override fun onSignalNeeded(signal: SignalData) {
        Log.d(TAG, "Signal needed: ${signal.type}. Sending to $targetUserId")
        targetUserId?.let {
            viewModel.sendSignal(it, signal)
        }
    }

    override fun onDataChannelOpened() {
        runOnUiThread {
            Toast.makeText(this, "Data Channel Opened! You can now select a file.", Toast.LENGTH_SHORT).show()
            binding.btnSelectFile.isEnabled = true
        }
    }

    override fun onDataChannelMessage(buffer: ByteBuffer) {
        runOnUiThread {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            // TODO: Implement logic to save the received bytes to a file.
            Toast.makeText(this, "File received! Size: ${bytes.size} bytes", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webRTCClient.close()
    }
}
