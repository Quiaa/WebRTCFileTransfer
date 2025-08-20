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

    private val _signalData = MutableLiveData<Pair<String, SignalData>>()
    val signalData: LiveData<Pair<String, SignalData>> = _signalData

    private val _signalError = MutableLiveData<String>()
    val signalError: LiveData<String> = _signalError

    init {
        listenForSignals()
    }

    fun sendSignal(targetUserId: String, signalData: SignalData) {
        viewModelScope.launch {
            try {
                repository.sendSignal(targetUserId, signalData)
            } catch (e: Exception) {
                _signalError.postValue("Failed to send signal: ${e.message}")
            }
        }
    }

    private fun listenForSignals() {
        viewModelScope.launch {
            repository.listenForSignals()
                .catch { exception ->
                    _signalError.postValue("Error listening for signals: ${exception.message}")
                }
                .collect { (sender, data) ->
                    _signalData.postValue(Pair(sender, data))
                }
        }
    }

    // Function to clear the signals from Firestore.
    fun clearSignals() {
        viewModelScope.launch {
            try {
                repository.clearSignals()
            } catch (e: Exception) {
                _signalError.postValue("Failed to clear signals: ${e.message}")
            }
        }
    }
}
