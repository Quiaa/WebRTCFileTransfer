package com.example.webrtcfiletransfer.ui.transfer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.data.repository.TransferRepository
import com.example.webrtcfiletransfer.databinding.DialogTransferRequestBinding
import com.example.webrtcfiletransfer.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.CharacterIterator
import java.text.StringCharacterIterator

class TransferRequestDialogFragment : DialogFragment() {

    private var _binding: DialogTransferRequestBinding? = null
    private val binding get() = _binding!!

    // Use the shared MainViewModel
    private val mainViewModel: MainViewModel by activityViewModels()

    private var senderId: String? = null
    private var filename: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        arguments?.let {
            senderId = it.getString(ARG_SENDER_ID)
            filename = it.getString(ARG_FILENAME)
            val filesize = it.getLong(ARG_FILESIZE)

            val fileSizeReadable = humanReadableByteCountSI(filesize)
            // In a real app, you'd fetch the username from the senderId
            binding.tvRequestInfo.text = "From: $senderId\nFile: $filename\nSize: $fileSizeReadable"
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnAccept.setOnClickListener {
            senderId?.let { id ->
                // Send the ACCEPT event via the ViewModel
                mainViewModel.sendAppEvent(id, SignalData(type = "TRANSFER_ACCEPT"))

                // Navigate to TransferActivity as the receiver
                val intent = Intent(requireContext(), TransferActivity::class.java).apply {
                    putExtra("target_user_id", id)
                    putExtra("target_username", filename) // Passing filename as a placeholder for username
                    putExtra("is_caller", false)
                }
                startActivity(intent)
                dismiss()
            }
        }

        binding.btnReject.setOnClickListener {
            senderId?.let { id ->
                // Send the REJECT event via the ViewModel
                mainViewModel.sendAppEvent(id, SignalData(type = "TRANSFER_REJECT"))
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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

    companion object {
        const val TAG = "TransferRequestDialog"
        private const val ARG_SENDER_ID = "sender_id"
        private const val ARG_FILENAME = "filename"
        private const val ARG_FILESIZE = "filesize"

        fun newInstance(senderId: String, signal: SignalData): TransferRequestDialogFragment {
            val args = Bundle().apply {
                putString(ARG_SENDER_ID, senderId)
                putString(ARG_FILENAME, signal.fileMetaData?.filename)
                putLong(ARG_FILESIZE, signal.fileMetaData?.fileSize ?: 0L)
            }
            return TransferRequestDialogFragment().apply {
                arguments = args
            }
        }
    }
}
