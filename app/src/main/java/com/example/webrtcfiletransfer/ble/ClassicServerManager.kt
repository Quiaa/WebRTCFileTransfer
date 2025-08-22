package com.example.webrtcfiletransfer.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.util.Log
import java.io.IOException
import java.util.*

class ClassicServerManager(
    private val bluetoothAdapter: BluetoothAdapter
) {
    private val TAG = "ClassicServer"
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null

    companion object {
        const val SERVICE_NAME = "WebRTCFileTransfer"
        val SERVICE_UUID: UUID = UUID.fromString("a60f35f0-b93a-11de-8a39-0800200c9a66") // A standard UUID for SPP
    }

    fun startServer(uid: String) {
        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
            acceptThread = Thread {
                Log.d(TAG, "Server socket started, waiting for connections...")
                while (true) {
                    try {
                        val socket = serverSocket?.accept()
                        Log.d(TAG, "Connection accepted. Writing UID.")
                        socket?.outputStream?.write(uid.toByteArray(Charsets.UTF_8))
                        socket?.outputStream?.flush()
                        // The client is responsible for closing the socket after reading.
                    } catch (e: IOException) {
                        Log.e(TAG, "Socket's accept() method failed or connection closed.", e)
                        break // Exit the loop
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Accept thread interrupted.", e)
                        break // Exit the loop
                    }
                }
            }
            acceptThread?.start()
        } catch (e: IOException) {
            Log.e(TAG, "Socket's listen() method failed", e)
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            acceptThread?.interrupt()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        }
    }
}
