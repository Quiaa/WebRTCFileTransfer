package com.example.webrtcfiletransfer.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.webrtcfiletransfer.databinding.ActivitySignupBinding
import com.example.webrtcfiletransfer.util.Resource
import com.example.webrtcfiletransfer.viewmodel.AuthViewModel

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    // Lazily initialize AuthViewModel using the activity-ktx library.
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()

        binding.tvLogin.setOnClickListener {
            finish()
        }

        binding.btnSignUp.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // Basic input validation.
            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.signUp(username, email, password)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to set up observers for LiveData from the ViewModel.
    private fun setupObservers() {
        viewModel.signUpState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    // Show progress bar while signing up.
                    binding.progressBar.visibility = View.VISIBLE
                }
                is Resource.Success -> {
                    // Hide progress bar and show success message.
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Sign up successful! Please log in.", Toast.LENGTH_LONG).show()
                    // Finish this activity to go back to LoginActivity.
                    finish()
                }
                is Resource.Error -> {
                    // Hide progress bar and show error message.
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
