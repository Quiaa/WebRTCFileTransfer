package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

data class GenericDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)

class BLEScanner(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val scanner = bluetoothAdapter.bluetoothLeScanner

    val discoveredDevices = MutableStateFlow<List<GenericDevice>>(emptyList())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val deviceName = it.device.name
                val address = it.device.address
                val rssi = it.rssi

                val genericDevice = GenericDevice(deviceName, address, rssi)
                val currentList = discoveredDevices.value.toMutableList()
                val existingDevice = currentList.find { d -> d.address == address }

                if (existingDevice != null) {
                    val index = currentList.indexOf(existingDevice)
                    currentList[index] = genericDevice
                } else {
                    currentList.add(genericDevice)
                }
                discoveredDevices.value = currentList
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.d("BLEScanner", "Batch scan results received")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLEScanner", "Scan failed with error code: $errorCode")
        }
    }

    fun startScan() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        Log.d("BLEScanner", "Scan started for all devices")
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        Log.d("BLEScanner", "Scan stopped")
    }
}
