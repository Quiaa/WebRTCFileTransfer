package com.example.webrtcfiletransfer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtcfiletransfer.data.repository.AuthRepository
import com.example.webrtcfiletransfer.util.Resource
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    // LiveData to observe the state of the sign-up process.
    private val _signUpState = MutableLiveData<Resource<Unit>>()
    val signUpState: LiveData<Resource<Unit>> = _signUpState

    // LiveData to observe the state of the login process.
    private val _loginState = MutableLiveData<Resource<Unit>>()
    val loginState: LiveData<Resource<Unit>> = _loginState

    // Function to initiate the sign-up process.
    fun signUp(username: String, email: String, password: String) {
        // Use viewModelScope to launch a coroutine that is automatically cancelled when the ViewModel is cleared.
        viewModelScope.launch {
            // Set the state to Loading before starting the operation.
            _signUpState.value = Resource.Loading()
            try {
                // Call the repository's signUp function.
                repository.signUp(username, email, password)
                // If successful, set the state to Success.
                _signUpState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                // If an error occurs, set the state to Error with the exception message.
                _signUpState.value = Resource.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    // Function to initiate the login process.
    fun login(email: String, password: String) {
        viewModelScope.launch {
            // Set the state to Loading.
            _loginState.value = Resource.Loading()
            try {
                // Call the repository's login function.
                repository.login(email, password)
                // If successful, set the state to Success.
                _loginState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                // If an error occurs, set the state to Error.
                _loginState.value = Resource.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }
}
