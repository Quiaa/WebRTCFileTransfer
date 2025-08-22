package com.example.webrtcfiletransfer.ui.transfer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.webrtcfiletransfer.data.model.CallSession
import com.example.webrtcfiletransfer.data.model.FileMetaData
import com.example.webrtcfiletransfer.databinding.ActivityTransferBinding
import com.example.webrtcfiletransfer.ui.BaseActivity
import com.example.webrtcfiletransfer.util.WebRTCClient
import com.example.webrtcfiletransfer.util.WebRTCListener
import com.example.webrtcfiletransfer.viewmodel.MainViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

// For chunking protocol
data class ControlMessage(val type: String, val filename: String?, val size: Long?)

class TransferActivity : BaseActivity(), WebRTCListener {

    private val TAG = "TransferActivity"
    private lateinit var binding: ActivityTransferBinding
    override val mainViewModel: MainViewModel by viewModels {
        com.example.webrtcfiletransfer.viewmodel.MainViewModelFactory(
            application,
            (getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter,
            com.example.webrtcfiletransfer.data.repository.MainRepository()
        )
    }
    private val gson = Gson()

    private lateinit var webRTCClient: WebRTCClient
    private var callId: String? = null
    private var targetUserId: String? = null
    private var targetUsername: String? = null
    private var isCaller = false
    private val role by lazy { if (isCaller) "Caller" else "Callee" }
    private var selectedFileUri: Uri? = null

    // Buffer for early ICE candidates
    private val iceCandidateBuffer = mutableListOf<IceCandidate>()

    // For receiving file
    private var fileOutputStream: ByteArrayOutputStream? = null
    private var receivedFilename: String? = null
    private var totalFileSize: Long = 0
    private var bytesReceived: Long = 0

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                binding.btnSendFile.isEnabled = true
                Toast.makeText(this, "File selected, ready to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                fileOutputStream?.toByteArray()?.let { content ->
                    saveFileToUri(uri, content)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val session = intent.getParcelableExtra<CallSession>("call_session")
        isCaller = intent.getBooleanExtra("is_caller", false)

        webRTCClient = WebRTCClient(application, this)

        if (isCaller) {
            targetUserId = intent.getStringExtra("target_user_id")
            targetUsername = intent.getStringExtra("target_username")
            if (targetUserId == null) {
                Log.e(TAG, "Target user ID is missing for caller.")
                finish()
                return
            }
            binding.tvTargetUser.text = "To: $targetUsername"
            binding.btnSelectFile.isEnabled = true
        } else {
            // Callee
            if (session == null || session.offer == null) {
                Log.e(TAG, "Call session or offer is missing for callee.")
                finish()
                return
            }
            callId = session.callId
            targetUserId = session.callerId
            binding.tvTargetUser.text = "From: ${session.fileMetaData?.filename}"
            binding.tvConnectionStatus.text = "Status: Accepted, connecting..."

            callId?.let { mainViewModel.observeCall(it, false) }

            val offerSdp = SessionDescription(SessionDescription.Type.OFFER, session.offer?.description)
            webRTCClient.onRemoteSessionReceived(offerSdp)

            Log.d(TAG, "($role) Creating answer...")
            webRTCClient.createAnswer()
        }

        setupClickListeners()
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.btnSelectFile.setOnClickListener { openFilePicker() }
        binding.btnSendFile.setOnClickListener {
            if (isCaller) {
                sendFileRequest()
            }
        }
        binding.btnSaveFile.setOnClickListener {
            receivedFilename?.let { filename ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
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

    private fun sendFileRequest() {
        if (!isCaller) return
        selectedFileUri?.let { uri ->
            try {
                val filename = getFileName(uri) ?: "unknown_file"
                val fileSize = getFileSize(uri) ?: 0L
                if (fileSize == 0L) {
                    Toast.makeText(this, "Cannot send empty file", Toast.LENGTH_SHORT).show()
                    return
                }
                val metadata = FileMetaData(filename, fileSize)
                binding.tvConnectionStatus.text = "Status: Calling..."

                Log.d(TAG, "($role) Creating offer...")
                webRTCClient.createOffer { offerSdp ->
                    targetUserId?.let {
                        mainViewModel.initiateCall(it, metadata, offerSdp)
                    }
                }
                binding.btnSendFile.isEnabled = false
                binding.btnSelectFile.isEnabled = false
            } catch (e: Exception) {
                Log.e(TAG, "Error creating transfer request", e)
            }
        }
    }

    private fun setupObservers() {
        mainViewModel.activeCallSession.observe(this) { session ->
            if (session == null) return@observe

            if(callId == null && isCaller) {
                Log.d(TAG, "($role) Call ID received: ${session.callId}. Draining ICE candidate buffer.")
                callId = session.callId
                // Drain the buffer now that we have the callId
                iceCandidateBuffer.forEach { candidate ->
                    mainViewModel.addIceCandidate(callId!!, candidate, isCaller)
                }
                iceCandidateBuffer.clear()
            }

            if (session.callId != this.callId) return@observe

            if (isCaller && session.answer != null) {
                session.answer?.let {
                    Log.d(TAG, "($role) Received answer. Setting remote description.")
                    webRTCClient.onRemoteSessionReceived(SessionDescription(SessionDescription.Type.ANSWER, it.description))
                }
            }
        }

        mainViewModel.remoteIceCandidateEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { candidate ->
                val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex ?: -1, candidate.sdp)
                webRTCClient.addIceCandidate(iceCandidate)
            }
        }
    }

    //region WebRTCListener Callbacks
    override fun onConnectionStateChange(state: PeerConnection.IceConnectionState) {
        runOnUiThread {
            binding.tvConnectionStatus.text = "Status: ${state.name}"
            if (state == PeerConnection.IceConnectionState.CONNECTED) {
                callId?.let { mainViewModel.updateCallStatus(it, "connected") }
            }
        }
    }

    override fun onSignalNeeded(sdp: SessionDescription) {
        Log.d(TAG, "($role) SDP signal needed: ${sdp.type}")
        if (sdp.type == SessionDescription.Type.ANSWER) {
            val session = intent.getParcelableExtra<CallSession>("call_session")
            session?.let { mainViewModel.acceptCall(it, sdp) }
        }
    }

    override fun onIceCandidateNeeded(candidate: IceCandidate) {
        if (callId == null) {
            Log.d(TAG, "($role) Buffering early ICE candidate.")
            iceCandidateBuffer.add(candidate)
        } else {
            mainViewModel.addIceCandidate(callId!!, candidate, isCaller)
        }
    }

    override fun onDataChannelOpened() {
        Log.d(TAG, "Data channel opened.")
        if (isCaller) {
            initiateFileSend()
        }
    }

    private fun initiateFileSend() {
        selectedFileUri?.let { uri ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val filename = getFileName(uri) ?: "unknown_file"
                    val fileSize = getFileSize(uri) ?: 0L
                    // 1. Send start control message
                    val startMsg = ControlMessage("start", filename, fileSize)
                    val startJson = gson.toJson(startMsg)
                    webRTCClient.sendData(ByteBuffer.wrap(startJson.toByteArray(StandardCharsets.UTF_8)))

                    runOnUiThread {
                        binding.pbTransferProgress.visibility = View.VISIBLE
                        binding.pbTransferProgress.max = fileSize.toInt()
                        binding.tvConnectionStatus.text = "Status: Sending..."
                    }

                    // 2. Send file in chunks
                    val inputStream = contentResolver.openInputStream(uri)
                    val buffer = ByteArray(65536) // 64KB chunks
                    var bytesRead: Int
                    var totalBytesSent: Long = 0
                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        val chunk = ByteBuffer.wrap(buffer, 0, bytesRead)
                        webRTCClient.sendData(chunk)
                        totalBytesSent += bytesRead
                        val progress = totalBytesSent.toInt()
                        runOnUiThread { binding.pbTransferProgress.progress = progress }
                    }
                    inputStream?.close()

                    // 3. Send end control message
                    val endMsg = ControlMessage("end", null, null)
                    val endJson = gson.toJson(endMsg)
                    webRTCClient.sendData(ByteBuffer.wrap(endJson.toByteArray(StandardCharsets.UTF_8)))

                    runOnUiThread {
                        binding.tvConnectionStatus.text = "Status: File Sent"
                        Toast.makeText(this@TransferActivity, "File sent successfully!", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Error reading or sending file", e)
                }
            }
        }
    }

    override fun onDataChannelMessage(buffer: ByteBuffer) {
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        try {
            // Try to parse as a control message first
            val json = String(data, StandardCharsets.UTF_8)
            val msg = gson.fromJson(json, ControlMessage::class.java)

            when (msg.type) {
                "start" -> {
                    fileOutputStream = ByteArrayOutputStream()
                    receivedFilename = msg.filename
                    totalFileSize = msg.size ?: 0
                    bytesReceived = 0
                    runOnUiThread {
                        binding.pbTransferProgress.visibility = View.VISIBLE
                        binding.pbTransferProgress.max = totalFileSize.toInt()
                        binding.tvConnectionStatus.text = "Status: Receiving..."
                    }
                }
                "end" -> {
                    val fileBytes = fileOutputStream?.toByteArray()
                    fileOutputStream?.close()
                    runOnUiThread {
                        binding.llReceivedFile.visibility = View.VISIBLE
                        binding.tvFileInfo.text = "Received: $receivedFilename"
                        binding.tvConnectionStatus.text = "Status: File Received"
                        binding.pbTransferProgress.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            // If JSON parsing fails, it's a file chunk
            fileOutputStream?.write(data)
            bytesReceived += data.size
            val progress = bytesReceived.toInt()
            runOnUiThread { binding.pbTransferProgress.progress = progress }
        }
    }
    //endregion

    //region Utility Functions
    private fun saveFileToUri(uri: Uri, content: ByteArray) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(content) }
            Toast.makeText(this, "File saved successfully!", Toast.LENGTH_LONG).show()
            binding.llReceivedFile.visibility = View.GONE
            fileOutputStream = null
            receivedFilename = null
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save file", e)
        }
    }

    private fun getFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun getFileSize(uri: Uri): Long? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (!cursor.isNull(sizeIndex)) return cursor.getLong(sizeIndex)
            }
        }
        return null
    }
    //endregion

    override fun onDestroy() {
        super.onDestroy()
        callId?.let { mainViewModel.endCall(it) }
        webRTCClient.close()
    }
}
