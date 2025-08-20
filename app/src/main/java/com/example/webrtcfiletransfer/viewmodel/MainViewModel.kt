package com.example.webrtcfiletransfer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.data.model.User
import com.example.webrtcfiletransfer.data.repository.MainRepository
import com.example.webrtcfiletransfer.util.Resource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = MainRepository()

    private val _usersState = MutableLiveData<Resource<List<User>>>()
    val usersState: LiveData<Resource<List<User>>> = _usersState

    // LiveData to notify the UI about an incoming call offer.
    private val _incomingOffer = MutableLiveData<Pair<String, SignalData>>()
    val incomingOffer: LiveData<Pair<String, SignalData>> = _incomingOffer

    init {
        // Start listening for offers as soon as the ViewModel is created.
        listenForIncomingOffers()
    }

    fun getUsers() {
        viewModelScope.launch {
            _usersState.value = Resource.Loading()
            try {
                val users = repository.getUsers()
                _usersState.value = Resource.Success(users)
            } catch (e: Exception) {
                _usersState.value = Resource.Error(e.message ?: "Failed to fetch users.")
            }
        }
    }

    private fun listenForIncomingOffers() {
        viewModelScope.launch {
            repository.listenForIncomingOffers()
                .catch { e ->
                    // Handle potential errors from the flow.
                }
                .collect { (sender, signal) ->
                    _incomingOffer.postValue(Pair(sender, signal))
                }
        }
    }

    fun signOut() {
        repository.signOut()
    }
}
