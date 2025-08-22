package com.example.webrtcfiletransfer.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.webrtcfiletransfer.ble.NearbyUser
import com.example.webrtcfiletransfer.data.model.User
import com.example.webrtcfiletransfer.databinding.ItemUserBinding
import kotlin.math.pow

class UserAdapter(
    private var users: List<NearbyUser>,
    private val onUserClicked: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // ViewHolder class to hold the view for each user item.
    inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(nearbyUser: NearbyUser) {
            binding.tvUsername.text = nearbyUser.user.username
            binding.tvRssi.text = "${nearbyUser.rssi} dBm"
            binding.tvDistance.text = "${calculateDistance(nearbyUser.rssi)} m"
            binding.root.setOnClickListener {
                onUserClicked(nearbyUser.user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    // Function to update the list of users in the adapter.
    fun updateUsers(newUsers: List<NearbyUser>) {
        users = newUsers
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
