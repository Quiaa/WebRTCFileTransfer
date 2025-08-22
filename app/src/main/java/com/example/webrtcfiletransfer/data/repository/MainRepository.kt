package com.example.webrtcfiletransfer.data.repository

import android.util.Log
import com.example.webrtcfiletransfer.data.model.FirestoreSignal
import com.example.webrtcfiletransfer.data.model.SignalData
import com.example.webrtcfiletransfer.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MainRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getUsers(): List<User> {
        val currentUserId = auth.currentUser?.uid ?: ""
        val usersCollection = db.collection("users").get().await()
        return usersCollection.documents.mapNotNull { it.toObject(User::class.java) }
            .filter { it.uid != currentUserId }
    }

    fun getUsersByUids(uids: List<String>): Flow<List<User>> = callbackFlow {
        if (uids.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val listener = db.collection("users")
            .whereIn("uid", uids)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull { it.toObject(User::class.java) } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    fun signOut() {
        auth.signOut()
    }

    // Listens for incoming connection offers (signals of type "OFFER").
    fun listenForIncomingOffers(): Flow<Pair<String, SignalData>> = callbackFlow {
        val currentUser = auth.currentUser ?: return@callbackFlow
        val docRef = db.collection("webrtc_signals").document(currentUser.uid)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    // Use toObject() for safe and automatic conversion.
                    val receivedSignal = snapshot.toObject(FirestoreSignal::class.java)

                    if (receivedSignal != null && receivedSignal.sender != currentUser.uid) {
                        // We only care about "OFFER" signals here to initiate a connection.
                        if (receivedSignal.signal.type == "OFFER") {
                            Log.d("MainRepository", "Incoming OFFER from: ${receivedSignal.sender}")
                            trySend(Pair(receivedSignal.sender, receivedSignal.signal))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainRepository", "Error parsing incoming offer", e)
                }
            }
        }
        awaitClose { listener.remove() }
    }
}
