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

class MainViewModel : ViewModel() {

    private val TAG = "MainViewModel"
    private val mainRepository = MainRepository()
    private val transferRepository = TransferRepository

    private val _usersState = MutableLiveData<Resource<List<User>>>()
    val usersState: LiveData<Resource<List<User>>> = _usersState

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
        getUsers()
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

    fun getUsers() {
        viewModelScope.launch {
            _usersState.value = Resource.Loading()
            try {
                val users = mainRepository.getUsers()
                _usersState.value = Resource.Success(users)
            } catch (e: Exception) {
                _usersState.value = Resource.Error(e.message ?: "Failed to fetch users.")
            }
        }
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

    fun signOut() {
        mainRepository.signOut()
    }
}
