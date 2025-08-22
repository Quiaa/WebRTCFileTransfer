package com.example.webrtcfiletransfer.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.webrtcfiletransfer.R
import com.example.webrtcfiletransfer.databinding.ActivityMainBinding
import com.example.webrtcfiletransfer.ui.BaseActivity
import com.example.webrtcfiletransfer.ui.auth.LoginActivity
import com.example.webrtcfiletransfer.ui.transfer.TransferActivity
import com.example.webrtcfiletransfer.util.Resource
import com.example.webrtcfiletransfer.viewmodel.MainViewModel
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import com.example.webrtcfiletransfer.ble.BLEAdvertiser
import com.example.webrtcfiletransfer.viewmodel.MainViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.example.webrtcfiletransfer.data.repository.MainRepository
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.webrtcfiletransfer.ble.GattServerManager
import com.example.webrtcfiletransfer.ble.VerificationResult
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    override val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            application,
            (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter,
            MainRepository()
        )
    }
    private lateinit var userAdapter: UserAdapter
    private lateinit var bleAdvertiser: BLEAdvertiser
    private var gattServerManager: GattServerManager? = null

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                startBleOperations()
            } else {
                Toast.makeText(this, "Permissions are required for this feature", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        bleAdvertiser = BLEAdvertiser(bluetoothAdapter)


        setupRecyclerView()
        setupObservers()

        if (hasPermissions()) {
            startBleOperations()
        } else {
            requestPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.stopDiscovery()
        bleAdvertiser.stopAdvertising()
        gattServerManager?.stopServer()
    }

    private fun startBleOperations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_LONG).show()
            goToLoginActivity()
            return
        }
        mainViewModel.startDiscovery()
        bleAdvertiser.startAdvertising(uid)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        gattServerManager = GattServerManager(this, bluetoothManager, uid)
        gattServerManager?.startServer()
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionsLauncher.launch(getRequiredPermissions())
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val switchItem = menu?.findItem(R.id.menu_show_nameless)
        val switchView = switchItem?.actionView as? com.google.android.material.switchmaterial.SwitchMaterial
        switchView?.setOnCheckedChangeListener { _, isChecked ->
            mainViewModel.setShowNamelessDevices(isChecked)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sign_out -> {
                mainViewModel.signOut()
                goToLoginActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(emptyList()) { device ->
            mainViewModel.verifyDevice(device.address)
        }
        binding.rvUsers.adapter = userAdapter
    }



    private fun setupObservers() {
        mainViewModel.discoveredDevices.observe(this) { devices ->
            userAdapter.updateDevices(devices)
        }

        mainViewModel.verificationResult.observe(this) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is VerificationResult.InProgress -> {
                        binding.verificationOverlay.visibility = View.VISIBLE
                    }
                    is VerificationResult.Success -> {
                        binding.verificationOverlay.visibility = View.GONE
                        lifecycleScope.launch {
                            val user = mainViewModel.getUserByUid(result.uid)
                            if (user != null) {
                                val intent = Intent(this@MainActivity, TransferActivity::class.java).apply {
                                    putExtra("target_user_id", user.uid)
                                    putExtra("target_username", user.username)
                                    putExtra("is_caller", true)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@MainActivity, "User not found in database", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is VerificationResult.Failure -> {
                        binding.verificationOverlay.visibility = View.GONE
                        showAppNotInstalledDialog()
                    }
                }
            }
        }

        // The logic for observing incoming offers is now handled globally by BaseActivity
        // which will show a DialogFragment for new transfer requests.
    }

    private fun showAppNotInstalledDialog() {
        AlertDialog.Builder(this)
            .setTitle("Application Not Found")
            .setMessage("The other user does not have this app installed. To share files, they need to install it.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Show QR Code") { dialog, _ ->
                // TODO: Implement QR code display
                Toast.makeText(this, "QR Code functionality not implemented yet.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun goToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
