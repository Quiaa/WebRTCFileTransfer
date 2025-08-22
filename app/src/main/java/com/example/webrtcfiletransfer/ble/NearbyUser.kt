package com.example.webrtcfiletransfer.ble

import com.example.webrtcfiletransfer.data.model.User

data class NearbyUser(
    val user: User,
    val rssi: Int
)
