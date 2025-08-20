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

    suspend fun sendSignal(targetUserId: String, signalData: SignalData) {
        val currentUser = auth.currentUser ?: return
        val firestoreSignal = FirestoreSignal(
            sender = currentUser.uid,
            signal = signalData
        )
        db.collection("webrtc_signals").document(targetUserId).set(firestoreSignal).await()
    }

    fun listenForSignals(): Flow<Pair<String, SignalData>> = callbackFlow {
        val currentUser = auth.currentUser ?: return@callbackFlow
        val docRef = db.collection("webrtc_signals").document(currentUser.uid)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    val receivedSignal = snapshot.toObject(FirestoreSignal::class.java)
                    if (receivedSignal != null && receivedSignal.sender != currentUser.uid) {
                        Log.d("TransferRepository", "Received signal from: ${receivedSignal.sender}")
                        trySend(Pair(receivedSignal.sender, receivedSignal.signal))
                    }
                } catch (e: Exception) {
                    Log.e("TransferRepository", "Error parsing signal data with toObject()", e)
                    close(e)
                }
            }
        }
        awaitClose { listener.remove() }
    }

    // Deletes the signaling document for the current user from Firestore.
    suspend fun clearSignals() {
        val currentUser = auth.currentUser ?: return
        db.collection("webrtc_signals").document(currentUser.uid).delete().await()
    }
}
