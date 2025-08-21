package com.example.webrtcfiletransfer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.data.model.User
import com.example.webrtcfiletransfer.data.repository.MainRepository
import com.example.webrtcfiletransfer.data.repository.TransferRepository
import com.example.webrtcfiletransfer.util.Event
import com.example.webrtcfiletransfer.util.Resource
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val mainRepository = MainRepository()
    private val transferRepository = TransferRepository

    private val _usersState = MutableLiveData<Resource<List<User>>>()
    val usersState: LiveData<Resource<List<User>>> = _usersState

    // LiveData for app-level events (e.g., transfer requests for the global dialog)
    private val _incomingAppEvent = MutableLiveData<Event<Pair<String, SignalData>>>()
    val incomingAppEvent: LiveData<Event<Pair<String, SignalData>>> = _incomingAppEvent

    // LiveData for WebRTC signals (for TransferActivity)
    private val _webrtcSignalEvent = MutableLiveData<Event<Pair<String, SignalData>>>()
    val webrtcSignalEvent: LiveData<Event<Pair<String, SignalData>>> = _webrtcSignalEvent

    init {
        // The listener is started in MyApplication, so we just need to observe the signals.
        observeAppEvents()
        observeWebRTCSignals()
        getUsers() // Fetch users on init
    }

    private fun observeAppEvents() {
        viewModelScope.launch {
            transferRepository.appEventFlow.collect { (sender, signal) ->
                _incomingAppEvent.postValue(Event(Pair(sender, signal)))
            }
        }
    }

    private fun observeWebRTCSignals() {
        viewModelScope.launch {
            transferRepository.webrtcSignalFlow.collect { (sender, signal) ->
                _webrtcSignalEvent.postValue(Event(Pair(sender, signal)))
            }
        }
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

    fun signOut() {
        mainRepository.signOut()
    }

    fun sendAppEvent(targetUserId: String, signalData: SignalData) {
        viewModelScope.launch {
            transferRepository.sendAppEvent(targetUserId, signalData)
        }
    }

    fun sendWebRTCSignal(targetUserId: String, signalData: SignalData) {
        viewModelScope.launch {
            transferRepository.sendWebRTCSignal(targetUserId, signalData)
        }
    }
}
