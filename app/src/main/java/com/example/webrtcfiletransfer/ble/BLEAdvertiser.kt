package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.*

class BLEAdvertiser(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter.bluetoothLeAdvertiser
    private val serviceUuid: ParcelUuid = ParcelUuid(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")) // A generic UUID

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLEAdvertiser", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLEAdvertiser", "Advertising failed to start with error code: $errorCode")
        }
    }

    fun startAdvertising(uid: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, uid.toByteArray(Charset.forName("UTF-8")))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        Log.d("BLEAdvertiser", "Advertising stopped")
    }
}
