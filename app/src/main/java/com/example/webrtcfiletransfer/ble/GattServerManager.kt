package com.example.webrtcfiletransfer.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*

class GattServerManager(
    private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val uid: String
) {
    private val TAG = "GattServerManager"

    private var gattServer: BluetoothGattServer? = null
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val UID_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001102-0000-1000-8000-00805F9B34FB")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Device connected: ${device?.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Device disconnected: ${device?.address}")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            Log.d(TAG, "Service added with status: $status")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic?.uuid == UID_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    uid.toByteArray(Charsets.UTF_8)
                )
                Log.d(TAG, "Sent UID to ${device?.address}")
            }
        }
    }

    fun startServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        if (gattServer == null) {
            Log.e(TAG, "Unable to create GATT server")
            return
        }

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val uidCharacteristic = BluetoothGattCharacteristic(
            UID_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(uidCharacteristic)
        gattServer?.addService(service)
    }

    fun stopServer() {
        gattServer?.close()
        gattServer = null
    }
}
