package com.example.webrtcfiletransfer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.webrtcfiletransfer.data.model.*
import com.example.webrtcfiletransfer.data.repository.MainRepository
import com.example.webrtcfiletransfer.data.repository.TransferRepository
import com.example.webrtcfiletransfer.util.Event
import com.example.webrtcfiletransfer.util.Resource
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.*
import com.example.webrtcfiletransfer.ble.ClassicVerifier
import com.example.webrtcfiletransfer.ble.DeviceScanner
import com.example.webrtcfiletransfer.ble.DiscoveredDevice
import com.example.webrtcfiletransfer.ble.VerificationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first



class MainViewModelFactory(
    private val application: Application,
    private val bluetoothAdapter: BluetoothAdapter,
    private val mainRepository: MainRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, bluetoothAdapter, mainRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainViewModel(
    application: Application,
    bluetoothAdapter: BluetoothAdapter,
    private val mainRepository: MainRepository
) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"
    private val transferRepository = TransferRepository
    private val classicScanner = DeviceScanner(application, bluetoothAdapter)

    private val _showNamelessDevices = MutableStateFlow(false)

    val discoveredDevices: LiveData<List<DiscoveredDevice>> = classicScanner.discoveredDevices
        .combine(_showNamelessDevices) { devices, showNameless ->
            val filteredDevices = if (showNameless) {
                devices
            } else {
                devices.filter { !it.device.name.isNullOrEmpty() }
            }
            // Sorting by RSSI is no longer meaningful as it's not reliable for classic devices
            filteredDevices
        }
        .asLiveData()

    private val _verificationResult = MutableLiveData<Event<VerificationResult>>()
    val verificationResult: LiveData<Event<VerificationResult>> = _verificationResult

    fun setShowNamelessDevices(show: Boolean) {
        _showNamelessDevices.value = show
    }

    fun verifyDevice(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch {
            _verificationResult.postValue(Event(VerificationResult.InProgress))
            val verifier = ClassicVerifier(device)
            val result = verifier.startVerification()
            _verificationResult.postValue(Event(result))
        }
    }

    // LiveData for incoming call events, observed by MainActivity to show a dialog.
    private val _incomingCallEvent = MutableLiveData<Event<CallSession>>()
    val incomingCallEvent: LiveData<Event<CallSession>> = _incomingCallEvent

    // LiveData for observing a single, active call session.
    private val _activeCallSession = MutableLiveData<CallSession>()
    val activeCallSession: LiveData<CallSession> = _activeCallSession

    // LiveData for observing remote ICE candidates for the active call.
    private val _remoteIceCandidateEvent = MutableLiveData<Event<IceCandidateModel>>()
    val remoteIceCandidateEvent: LiveData<Event<IceCandidateModel>> = _remoteIceCandidateEvent


    init {
        listenForIncomingCalls()
    }

    fun startDiscovery() {
        classicScanner.startDiscovery()
    }

    fun stopDiscovery() {
        classicScanner.stopDiscovery()
    }

    private fun listenForIncomingCalls() {
        transferRepository.listenForIncomingCalls().onEach { session ->
            _incomingCallEvent.postValue(Event(session))
        }.launchIn(viewModelScope)
    }

    fun observeCall(callId: String, isCaller: Boolean) {
        transferRepository.observeCall(callId).onEach { session ->
            Log.d(TAG, "Call update for ${session.callId}: status=${session.status}, hasAnswer=${session.answer != null}")
            _activeCallSession.postValue(session)
        }.launchIn(viewModelScope)

        transferRepository.observeIceCandidates(callId, isCaller).onEach { candidate ->
            _remoteIceCandidateEvent.postValue(Event(candidate))
        }.launchIn(viewModelScope)
    }

    fun acceptingCall(callId: String) {
        viewModelScope.launch {
            transferRepository.updateStatus(callId, "accepting")
        }
    }

    fun initiateCall(calleeId: String, fileMetaData: FileMetaData, offer: SessionDescription) {
        viewModelScope.launch {
            val sdp = Sdp(offer.type.canonicalForm(), offer.description)
            val callId = transferRepository.initiateCall(calleeId, fileMetaData, sdp)
            Log.d(TAG, "Initiating call $callId for callee $calleeId")
            observeCall(callId, true)
        }
    }

    fun acceptCall(session: CallSession, answer: SessionDescription) {
        viewModelScope.launch {
            session.callId?.let {
                Log.d(TAG, "Accepting call $it")
                val sdp = Sdp(answer.type.canonicalForm(), answer.description)
                transferRepository.updateAnswer(it, sdp)
                // Start observing the call after accepting
                observeCall(it, false)
            }
        }
    }

    fun addIceCandidate(callId: String, candidate: IceCandidate, isCaller: Boolean) {
        viewModelScope.launch {
            val iceCandidateModel = IceCandidateModel(
                sdp = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
            transferRepository.addIceCandidate(callId, iceCandidateModel, isCaller)
        }
    }

    fun updateCallStatus(callId: String, status: String) {
        viewModelScope.launch {
            transferRepository.updateStatus(callId, status)
        }
    }

    fun endCall(callId: String) {
        viewModelScope.launch {
            transferRepository.deleteCall(callId)
        }
    }

    suspend fun getUserByUid(uid: String): User? {
        return mainRepository.getUsersByUids(listOf(uid)).first().firstOrNull()
    }

    fun signOut() {
        mainRepository.signOut()
    }
}
