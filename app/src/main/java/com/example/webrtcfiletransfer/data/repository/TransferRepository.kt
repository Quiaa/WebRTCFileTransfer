package com.example.webrtcfiletransfer.data.repository

import android.util.Log
import com.example.webrtcfiletransfer.data.model.FirestoreSignal
import com.example.webrtcfiletransfer.data.model.SignalData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class TransferRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // This function sends the signal data to the target user.
    suspend fun sendSignal(targetUserId: String, signalData: SignalData) {
        val currentUser = auth.currentUser ?: return

        // Create an instance of our structured data class.
        val firestoreSignal = FirestoreSignal(
            sender = currentUser.uid,
            signal = signalData
        )

        // Firestore automatically serializes the entire firestoreSignal object.
        db.collection("webrtc_signals").document(targetUserId).set(firestoreSignal).await()
    }

    // This function listens for incoming signals.
    fun listenForSignals(): Flow<Pair<String, SignalData>> = callbackFlow {
        val currentUser = auth.currentUser ?: return@callbackFlow
        val docRef = db.collection("webrtc_signals").document(currentUser.uid)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error) // Close the flow on error.
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    // Use toObject() for safe and automatic conversion from Firestore document to our data class.
                    val receivedSignal = snapshot.toObject(FirestoreSignal::class.java)

                    if (receivedSignal != null) {
                        // CRITICAL FIX: Ignore signals sent by the current user.
                        if (receivedSignal.sender != currentUser.uid) {
                            Log.d("TransferRepository", "Received signal from: ${receivedSignal.sender}")
                            // Send the valid signal to the ViewModel.
                            trySend(Pair(receivedSignal.sender, receivedSignal.signal))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TransferRepository", "Error parsing signal data with toObject()", e)
                    close(e) // Close flow on parsing error.
                }
            }
        }
        // This block is called when the flow is cancelled.
        // It's important to remove the Firestore listener to prevent memory leaks.
        awaitClose { listener.remove() }
    }
}
