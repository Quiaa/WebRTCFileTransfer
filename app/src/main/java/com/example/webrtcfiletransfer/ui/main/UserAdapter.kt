package com.example.webrtcfiletransfer.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.webrtcfiletransfer.ble.GenericDevice
import com.example.webrtcfiletransfer.databinding.ItemUserBinding
import kotlin.math.pow

class UserAdapter(
    private var devices: List<GenericDevice>,
    private val onDeviceClicked: (GenericDevice) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // ViewHolder class to hold the view for each user item.
    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: GenericDevice) {
            binding.tvUsername.text = device.name ?: device.address
            binding.tvRssi.text = "${device.rssi} dBm"
            val distance = calculateDistance(device.rssi)
            if (distance > 0) {
                binding.tvDistance.text = String.format("%.3f m", distance)
            } else {
                binding.tvDistance.text = "N/A"
            }
            binding.root.setOnClickListener {
                onDeviceClicked(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    // Function to update the list of devices in the adapter.
    fun updateDevices(newDevices: List<GenericDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    private fun calculateDistance(rssi: Int, txPower: Int = -59): Double {
        // txPower is the received signal strength at 1 meter.
        // This value can be calibrated for better accuracy.
        if (rssi == 0) {
            return -1.0 // Cannot determine distance
        }
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10)
        } else {
            (0.89976) * ratio.pow(7.7095) + 0.111
        }
    }
}