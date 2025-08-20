package com.example.webrtcfiletransfer.ui.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcfiletransfer.data.model.FileTransferData
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.databinding.ActivityTransferBinding
import com.example.webrtcfiletransfer.util.WebRTCClient
import com.example.webrtcfiletransfer.util.WebRTCListener
import com.example.webrtcfiletransfer.viewmodel.TransferViewModel
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.CharacterIterator
import java.text.StringCharacterIterator

class TransferActivity : AppCompatActivity(), WebRTCListener {

    private val TAG = "TransferActivity"
    private lateinit var binding: ActivityTransferBinding
    private val viewModel: TransferViewModel by viewModels()
    private val gson = Gson()

    private lateinit var webRTCClient: WebRTCClient
    private var targetUserId: String? = null
    private var targetUsername: String? = null
    private var isCaller = false
    private var selectedFileUri: Uri? = null

    // Buffer for received file data
    private var receivedFileContent: ByteArray? = null
    private var receivedFilename: String? = null

    private val remoteIceCandidateBuffer = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                binding.btnSendFile.isEnabled = true
                Toast.makeText(this, "File selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Activity Result Launcher for saving the file.
    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                receivedFileContent?.let { content ->
                    saveFileToUri(uri, content)
                }
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
        binding.btnSelectFile.setOnClickListener { openFilePicker() }
        binding.btnSendFile.setOnClickListener { sendFile() }
        binding.btnSaveFile.setOnClickListener {
            receivedFilename?.let { filename ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // You can be more specific if you know the mime type
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                saveFileLauncher.launch(intent)
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        filePickerLauncher.launch(intent)
    }

    private fun sendFile() {
        selectedFileUri?.let { uri ->
            try {
                val filename = getFileName(uri) ?: "unknown_file"
                val inputStream = contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()

                if (fileBytes != null) {
                    val base64Data = Base64.encodeToString(fileBytes, Base64.DEFAULT)
                    val fileTransferData = FileTransferData(filename, base64Data)
                    val json = gson.toJson(fileTransferData)
                    webRTCClient.sendData(ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8)))
                    Toast.makeText(this, "Sending file...", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading or sending file", e)
                Toast.makeText(this, "Error sending file", Toast.LENGTH_SHORT).show()
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
                        isRemoteDescriptionSet = true
                        drainIceCandidateBuffer()
                        webRTCClient.createAnswer()
                    }
                }
                "ANSWER" -> {
                    signal.sdp?.let {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, it)
                        webRTCClient.onRemoteSessionReceived(sdp)
                        isRemoteDescriptionSet = true
                        drainIceCandidateBuffer()
                    }
                }
                "ICE_CANDIDATE" -> {
                    if (signal.sdp != null && signal.sdpMid != null && signal.sdpMLineIndex != null) {
                        val candidate = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.sdp)
                        if (isRemoteDescriptionSet) {
                            webRTCClient.addIceCandidate(candidate)
                        } else {
                            remoteIceCandidateBuffer.add(candidate)
                        }
                    }
                }
            }
        }
        viewModel.signalError.observe(this) { error ->
            Toast.makeText(this, "Signaling error: $error", Toast.LENGTH_LONG).show()
        }
    }

    private fun drainIceCandidateBuffer() {
        if (remoteIceCandidateBuffer.isNotEmpty()) {
            Log.d(TAG, "Draining ${remoteIceCandidateBuffer.size} buffered ICE candidates.")
            remoteIceCandidateBuffer.forEach { webRTCClient.addIceCandidate(it) }
            remoteIceCandidateBuffer.clear()
        }
    }

    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        runOnUiThread { binding.tvConnectionStatus.text = "Status: ${state.name}" }
    }

    override fun onSignalNeeded(signal: SignalData) {
        Log.d(TAG, "Signal needed: ${signal.type}. Sending to $targetUserId")
        targetUserId?.let { viewModel.sendSignal(it, signal) }
    }

    override fun onDataChannelOpened() {
        runOnUiThread {
            Toast.makeText(this, "Data Channel Opened! You can now select a file.", Toast.LENGTH_SHORT).show()
            binding.btnSelectFile.isEnabled = true
        }
    }

    override fun onDataChannelMessage(buffer: ByteBuffer) {
        runOnUiThread {
            try {
                val json = StandardCharsets.UTF_8.decode(buffer).toString()
                val fileData = gson.fromJson(json, FileTransferData::class.java)

                // Store received data in buffer
                receivedFileContent = Base64.decode(fileData.data, Base64.DEFAULT)
                receivedFilename = fileData.filename

                // Update UI to show save options
                binding.llReceivedFile.visibility = View.VISIBLE
                val fileSizeReadable = humanReadableByteCountSI(receivedFileContent?.size?.toLong() ?: 0)
                binding.tvFileInfo.text = "Received file: $receivedFilename ($fileSizeReadable)"

            } catch (e: Exception) {
                Log.e(TAG, "Error processing received data", e)
                Toast.makeText(this, "Failed to process received file.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveFileToUri(uri: Uri, content: ByteArray) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content)
            }
            Toast.makeText(this, "File saved successfully!", Toast.LENGTH_LONG).show()
            // Hide the save UI after saving
            binding.llReceivedFile.visibility = View.GONE
            receivedFileContent = null
            receivedFilename = null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save file to URI", e)
            Toast.makeText(this, "Error saving file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    // Helper function to format byte count into a human-readable string (e.g., 1.2 MB)
    private fun humanReadableByteCountSI(bytes: Long): String {
        if (-1000 < bytes && bytes < 1000) {
            return "$bytes B"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        var tempBytes = bytes
        while (tempBytes <= -999950 || tempBytes >= 999950) {
            tempBytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", tempBytes / 1000.0, ci.current())
    }


    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearSignals()
        webRTCClient.close()
    }
}
