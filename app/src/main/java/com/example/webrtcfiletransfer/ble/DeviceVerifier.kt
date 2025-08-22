package com.example.webrtcfiletransfer.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class VerificationResult {
    object InProgress : VerificationResult()
    data class Success(val uid: String) : VerificationResult()
    data class Failure(val message: String) : VerificationResult()
}

class DeviceVerifier(
    private val context: Context,
    private val deviceAddress: String
) {
    private val TAG = "DeviceVerifier"
    private var gatt: BluetoothGatt? = null

    private val _result = MutableStateFlow<VerificationResult>(VerificationResult.InProgress)
    val result: StateFlow<VerificationResult> = _result

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.")
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.")
                    close()
                }
            } else {
                _result.value = VerificationResult.Failure("Connection failed with status: $status")
                close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(GattServerManager.SERVICE_UUID)
                if (service == null) {
                    _result.value = VerificationResult.Failure("App service not found on this device.")
                    close()
                    return
                }
                val characteristic = service.getCharacteristic(GattServerManager.UID_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    _result.value = VerificationResult.Failure("UID characteristic not found.")
                    close()
                    return
                }
                gatt.readCharacteristic(characteristic)
            } else {
                _result.value = VerificationResult.Failure("Service discovery failed with status: $status")
                close()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uid = value.toString(Charsets.UTF_8)
                _result.value = VerificationResult.Success(uid)
            } else {
                _result.value = VerificationResult.Failure("Characteristic read failed with status: $status")
            }
            close()
        }
    }

    fun startVerification() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun close() {
        gatt?.close()
        gatt = null
    }
}
