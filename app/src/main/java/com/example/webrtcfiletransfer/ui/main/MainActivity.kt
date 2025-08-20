package com.example.webrtcfiletransfer.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.example.webrtcfiletransfer.R
import com.example.webrtcfiletransfer.databinding.ActivityMainBinding
import com.example.webrtcfiletransfer.ui.BaseActivity
import com.example.webrtcfiletransfer.ui.auth.LoginActivity
import com.example.webrtcfiletransfer.ui.transfer.TransferActivity
import com.example.webrtcfiletransfer.util.Resource
import com.example.webrtcfiletransfer.viewmodel.MainViewModel

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var userAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupObservers()

        viewModel.getUsers()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sign_out -> {
                viewModel.signOut()
                goToLoginActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(emptyList()) { user ->
            // When a user clicks on a name, they become the CALLER.
            val intent = Intent(this, TransferActivity::class.java).apply {
                putExtra("target_user_id", user.uid)
                putExtra("target_username", user.username)
                putExtra("is_caller", true) // Explicitly set this user as the caller.
            }
            startActivity(intent)
        }
        binding.rvUsers.adapter = userAdapter
    }

    private fun setupObservers() {
        viewModel.usersState.observe(this) { state ->
            when (state) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    state.data?.let { userAdapter.updateUsers(it) }
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        // The logic for observing incoming offers is now handled globally by BaseActivity
        // which will show a DialogFragment for new transfer requests.
    }

    private fun goToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
