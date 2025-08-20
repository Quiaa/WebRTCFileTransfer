package com.example.webrtcfiletransfer.data.repository

import android.util.Log
import com.example.webrtcfiletransfer.data.model.FirestoreSignal
import com.example.webrtcfiletransfer.data.model.SignalData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

object TransferRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private const val WEBRTC_SIGNALS_COLLECTION = "webrtc_signals"
    private const val APP_EVENTS_COLLECTION = "app_events"

    private val _webrtcSignalFlow = MutableSharedFlow<Pair<String, SignalData>>()
    val webrtcSignalFlow = _webrtcSignalFlow.asSharedFlow()

    private val _appEventFlow = MutableSharedFlow<Pair<String, SignalData>>()
    val appEventFlow = _appEventFlow.asSharedFlow()

    suspend fun sendWebRTCSignal(targetUserId: String, signalData: SignalData) {
        sendSignalToCollection(WEBRTC_SIGNALS_COLLECTION, targetUserId, signalData)
    }

    suspend fun sendAppEvent(targetUserId: String, signalData: SignalData) {
        sendSignalToCollection(APP_EVENTS_COLLECTION, targetUserId, signalData)
    }

    private suspend fun sendSignalToCollection(collection: String, targetUserId: String, signalData: SignalData) {
        val currentUser = auth.currentUser ?: return
        val firestoreSignal = FirestoreSignal(sender = currentUser.uid, signal = signalData)
        db.collection(collection).document(targetUserId).set(firestoreSignal).await()
    }

    fun startListening() {
        startListeningOnCollection(WEBRTC_SIGNALS_COLLECTION) { sender, signal ->
            _webrtcSignalFlow.tryEmit(Pair(sender, signal))
        }
        startListeningOnCollection(APP_EVENTS_COLLECTION) { sender, signal ->
            _appEventFlow.tryEmit(Pair(sender, signal))
        }
    }

    private fun startListeningOnCollection(collection: String, onSignal: (String, SignalData) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val docRef = db.collection(collection).document(currentUser.uid)

        docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("TransferRepository", "Listen error on $collection", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    val receivedSignal = snapshot.toObject(FirestoreSignal::class.java)
                    if (receivedSignal != null && receivedSignal.sender != currentUser.uid) {
                        Log.d("TransferRepository", "Received signal on $collection from: ${receivedSignal.sender}")
                        onSignal(receivedSignal.sender, receivedSignal.signal)
                        // After processing, clear the document to prevent re-processing
                        snapshot.reference.delete()
                    }
                } catch (e: Exception) {
                    Log.e("TransferRepository", "Error parsing signal on $collection", e)
                }
            }
        }
    }

    suspend fun clearAllSignalsForCurrentUser() {
        val currentUser = auth.currentUser ?: return
        db.collection(WEBRTC_SIGNALS_COLLECTION).document(currentUser.uid).delete().await()
        db.collection(APP_EVENTS_COLLECTION).document(currentUser.uid).delete().await()
    }
}
