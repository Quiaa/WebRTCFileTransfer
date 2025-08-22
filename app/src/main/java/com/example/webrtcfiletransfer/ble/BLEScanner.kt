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

data class DiscoveredUser(
    val uid: String,
    val rssi: Int,
    val address: String
)

class BLEScanner(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val scanner = bluetoothAdapter.bluetoothLeScanner
    private val serviceUuid: ParcelUuid = ParcelUuid(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))

    val discoveredUsers = MutableStateFlow<List<DiscoveredUser>>(emptyList())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val serviceData = it.scanRecord?.getServiceData(serviceUuid)
                val uid = serviceData?.toString(Charsets.UTF_8) ?: return@let
                val rssi = it.rssi
                val address = it.device.address

                val discoveredUser = DiscoveredUser(uid, rssi, address)
                val currentList = discoveredUsers.value.toMutableList()
                val existingDevice = currentList.find { d -> d.address == address }

                if (existingDevice != null) {
                    val index = currentList.indexOf(existingDevice)
                    currentList[index] = discoveredUser
                } else {
                    currentList.add(discoveredUser)
                }
                discoveredUsers.value = currentList
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
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(serviceUuid)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        Log.d("BLEScanner", "Scan started")
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        Log.d("BLEScanner", "Scan stopped")
    }
}
