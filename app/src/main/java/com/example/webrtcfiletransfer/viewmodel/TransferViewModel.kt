package com.example.webrtcfiletransfer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.data.repository.TransferRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TransferViewModel : ViewModel() {

    private val repository = TransferRepository()

    // LiveData to hold incoming signaling data.
    private val _signalData = MutableLiveData<Pair<String, SignalData>>()
    val signalData: LiveData<Pair<String, SignalData>> = _signalData

    // LiveData to report any errors during signal listening.
    private val _signalError = MutableLiveData<String>()
    val signalError: LiveData<String> = _signalError

    init {
        // Start listening for signals as soon as the ViewModel is created.
        listenForSignals()
    }

    // Sends a signal to the specified target user.
    fun sendSignal(targetUserId: String, signalData: SignalData) {
        viewModelScope.launch {
            try {
                repository.sendSignal(targetUserId, signalData)
            } catch (e: Exception) {
                _signalError.postValue("Failed to send signal: ${e.message}")
            }
        }
    }

    // Listens for incoming signals and updates the LiveData.
    private fun listenForSignals() {
        viewModelScope.launch {
            repository.listenForSignals()
                .catch { exception ->
                    // Handle any errors from the flow.
                    _signalError.postValue("Error listening for signals: ${exception.message}")
                }
                .collect { (sender, data) ->
                    // Post the received data to the LiveData.
                    _signalData.postValue(Pair(sender, data))
                }
        }
    }
}
