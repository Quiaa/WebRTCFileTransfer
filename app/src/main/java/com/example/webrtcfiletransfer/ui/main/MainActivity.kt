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
import com.example.webrtcfiletransfer.viewmodel.MainViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.example.webrtcfiletransfer.ble.ClassicServerManager
import com.example.webrtcfiletransfer.data.repository.MainRepository
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.webrtcfiletransfer.ble.VerificationResult
import kotlinx.coroutines.launch
import android.content.Context
import android.location.LocationManager
import android.provider.Settings

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
    private var classicServerManager: ClassicServerManager? = null

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                checkBluetoothStateAndStart()
            } else {
                Toast.makeText(this, "Permissions are required for this feature", Toast.LENGTH_LONG).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startBleOperations()
            } else {
                Toast.makeText(this, "Bluetooth is required to discover devices.", Toast.LENGTH_LONG).show()
            }
        }

    private val makeDiscoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_CANCELED) {
                Toast.makeText(this, "Device is now discoverable.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Discoverability request cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // Perform checks every time the activity is resumed.
        if (hasPermissions()) {
            checkBluetoothStateAndStart()
        } else {
            // This will request permissions if they are missing.
            // If already requested, it won't do anything until the user responds.
            requestPermissions()
        }
    }

    private fun checkBluetoothStateAndStart() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // For Android 12 and above, BLUETOOTH_CONNECT permission is required to request enabling Bluetooth.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    enableBluetoothLauncher.launch(enableBtIntent)
                } else {
                    // This case should ideally not be hit if permissions are handled correctly before calling this.
                    Toast.makeText(this, "Bluetooth connect permission not granted.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // For older versions, no special permission is required.
                enableBluetoothLauncher.launch(enableBtIntent)
            }
        } else {
            startBleOperations()
        }
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.stopDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        classicServerManager?.stopServer()
    }

    private fun startBleOperations() {
        if (!isLocationEnabled()) {
            showLocationRequiredDialog()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You are not logged in.", Toast.LENGTH_LONG).show()
            goToLoginActivity()
            return
        }
        mainViewModel.startDiscovery()
        if (classicServerManager == null) {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            classicServerManager = ClassicServerManager(bluetoothManager.adapter)
            classicServerManager?.startServer(uid)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Services Required")
            .setMessage("Bluetooth scanning requires that you enable Location Services.")
            .setPositiveButton("Enable Location") { dialog, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
            R.id.menu_refresh_discoverable -> {
                refreshAndMakeDiscoverable()
                true
            }
            R.id.menu_sign_out -> {
                mainViewModel.signOut()
                goToLoginActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshAndMakeDiscoverable() {
        // Manually trigger the full check and start flow.
        // This will handle BT state, location state, and then call startDiscovery().
        checkBluetoothStateAndStart()
        Toast.makeText(this, "Refreshing device list...", Toast.LENGTH_SHORT).show()

        // Request to be discoverable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth advertise permission not granted. Cannot be discoverable.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        makeDiscoverableLauncher.launch(discoverableIntent)
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(emptyList()) { device ->
            mainViewModel.verifyDevice(device.device)
        }
        binding.rvUsers.adapter = userAdapter
    }

    private fun setupObservers() {
        mainViewModel.discoveredDevices.observe(this) { devices ->
            userAdapter.updateDevices(devices)
        }

        mainViewModel.verificationResult.observe(this) { event ->
            event.getContentIfNotHandled()?.let { result ->
                binding.verificationOverlay.visibility = View.GONE
                when (result) {
                    is VerificationResult.InProgress -> {
                        binding.verificationOverlay.visibility = View.VISIBLE
                    }
                    is VerificationResult.Success -> {
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
                                Toast.makeText(this@MainActivity, "User not found for UID: ${result.uid}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is VerificationResult.Failure -> {
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
