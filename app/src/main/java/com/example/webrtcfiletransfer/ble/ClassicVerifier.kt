package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ClassicVerifier(
    private val device: BluetoothDevice
) {
    private val TAG = "ClassicVerifier"
    private var socket: BluetoothSocket? = null

    suspend fun startVerification(): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating RFCOMM socket to ${device.address}")
                socket = device.createRfcommSocketToServiceRecord(ClassicServerManager.SERVICE_UUID)
                socket?.connect() // This is a blocking call

                Log.d(TAG, "Connected. Reading UID.")
                val inputStream = socket?.inputStream
                val buffer = ByteArray(1024)
                val bytes = inputStream?.read(buffer)
                if (bytes != null && bytes > 0) {
                    val uid = String(buffer, 0, bytes)
                    Log.d(TAG, "Verification success. UID: $uid")
                    VerificationResult.Success(uid)
                } else {
                    VerificationResult.Failure("Failed to read UID.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed for device ${device.address}", e)
                VerificationResult.Failure("Device does not have the app or is not ready.")
            } finally {
                close()
            }
        }
    }

    private fun close() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }
    }
}
