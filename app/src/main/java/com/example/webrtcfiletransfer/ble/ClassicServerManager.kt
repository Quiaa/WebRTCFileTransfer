package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class ClassicServerManager(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val TAG = "ClassicServer"
    private var serverSocket: BluetoothServerSocket? = null

    companion object {
        const val SERVICE_NAME = "WebRTCFileTransfer"
        val SERVICE_UUID: UUID = UUID.fromString("a60f35f0-b93a-11de-8a39-0800200c9a66") // A standard UUID for SPP
        private var serverJob: Job? = null
    }

    fun startServer(uid: String) {
        if (serverJob?.isActive == true) {
            Log.d(TAG, "Server is already running.")
            return
        }
        serverJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                Log.d(TAG, "Server socket started, waiting for connections...")
                while (isActive) {
                    try {
                        val socket = serverSocket?.accept()
                        Log.d(TAG, "Connection accepted. Writing UID.")
                        socket?.outputStream?.write(uid.toByteArray(Charsets.UTF_8))
                        socket?.outputStream?.flush()
                        // The client is responsible for closing the socket after reading.
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(TAG, "Socket's accept() method failed or connection closed.", e)
                        }
                        break // Exit the loop on error
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Socket's listen() method failed", e)
                }
            } finally {
                Log.d(TAG, "Server job finished.")
            }
        }
    }

    fun stopServer() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
            serverJob = null
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        }
    }
}
