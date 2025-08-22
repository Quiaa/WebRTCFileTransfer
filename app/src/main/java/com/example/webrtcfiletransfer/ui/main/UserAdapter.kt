package com.example.webrtcfiletransfer.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.webrtcfiletransfer.ble.DiscoveredDevice
import com.example.webrtcfiletransfer.databinding.ItemUserBinding

class UserAdapter(
    private var devices: List<DiscoveredDevice>,
    private val onDeviceClicked: (DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // ViewHolder class to hold the view for each user item.
    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                // Ensure the position is valid before processing the click.
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDeviceClicked(devices[adapterPosition])
                }
            }
        }

        fun bind(device: DiscoveredDevice) {
            binding.tvUsername.text = device.device.name ?: device.device.address
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
    fun updateDevices(newDevices: List<DiscoveredDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
