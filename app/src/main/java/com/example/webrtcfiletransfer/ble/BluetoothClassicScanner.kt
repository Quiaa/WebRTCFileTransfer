package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BluetoothClassicScanner(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val TAG = "ClassicScanner"
    private val _discoveredDevices = MutableStateFlow<List<GenericDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<GenericDevice>> = _discoveredDevices

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        } else {
                            -100 // RSSI not available on older versions this way
                        }
                        val genericDevice = GenericDevice(it.name, it.address, rssi)
                        val currentList = _discoveredDevices.value.toMutableList()
                        if (currentList.none { d -> d.address == genericDevice.address }) {
                            currentList.add(genericDevice)
                            _discoveredDevices.value = currentList
                            Log.d(TAG, "Found classic device: ${genericDevice.name ?: genericDevice.address}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Classic discovery finished.")
                    // Optionally, restart discovery here to make it continuous
                }
            }
        }
    }

    fun startDiscovery() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(receiver, filter)
        bluetoothAdapter.startDiscovery()
        Log.d(TAG, "Started classic discovery.")
    }

    fun stopDiscovery() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered, skipping unregister.")
        }
        Log.d(TAG, "Stopped classic discovery.")
    }
}
