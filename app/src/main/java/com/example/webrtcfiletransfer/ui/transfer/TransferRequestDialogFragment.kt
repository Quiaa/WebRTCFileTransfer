package com.example.webrtcfiletransfer.ui.transfer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.webrtcfiletransfer.data.model.CallSession
import com.example.webrtcfiletransfer.databinding.DialogTransferRequestBinding
import com.example.webrtcfiletransfer.viewmodel.MainViewModel
import java.text.CharacterIterator
import java.text.StringCharacterIterator

class TransferRequestDialogFragment : DialogFragment() {

    private var _binding: DialogTransferRequestBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private var callSession: CallSession? = null

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
            callSession = it.getParcelable(ARG_SESSION)
            val filename = callSession?.fileMetaData?.filename ?: "Unknown file"
            val filesize = callSession?.fileMetaData?.size ?: 0L
            val callerId = callSession?.callerId ?: "Unknown user"

            val fileSizeReadable = humanReadableByteCountSI(filesize)
            binding.tvRequestInfo.text = "From: $callerId\nFile: $filename\nSize: $fileSizeReadable"
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnAccept.setOnClickListener {
            callSession?.let { session ->
                // First, update the status to "accepting" to prevent the dialog from reappearing.
                session.callId?.let { mainViewModel.acceptingCall(it) }

                // Then, navigate to TransferActivity as the receiver
                val intent = Intent(requireContext(), TransferActivity::class.java).apply {
                    putExtra("call_session", session)
                    putExtra("is_caller", false)
                }
                startActivity(intent)
                dismiss()
            }
        }

        binding.btnReject.setOnClickListener {
            callSession?.callId?.let { id ->
                mainViewModel.endCall(id)
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
        private const val ARG_SESSION = "session"

        fun newInstance(session: CallSession): TransferRequestDialogFragment {
            val args = Bundle().apply {
                putParcelable(ARG_SESSION, session)
            }
            return TransferRequestDialogFragment().apply {
                arguments = args
            }
        }
    }
}
