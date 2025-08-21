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
        db.collection(collection).document(targetUserId).collection("signals").add(firestoreSignal).await()
    }

    fun startListening() {
        startListeningOnCollection(WEBRTC_SIGNALS_COLLECTION) { sender, signal ->
            _webrtcSignalFlow.tryEmit(Pair(sender, signal))
        }
        startListeningOnCollection(APP_EVENTS_COLLECTION) { sender, signal ->
            _appEventFlow.tryEmit(Pair(sender, signal))
        }
    }

    private fun startListeningOnCollection(collectionName: String, onSignal: (String, SignalData) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val signalsCollection = db.collection(collectionName).document(currentUser.uid).collection("signals")

        signalsCollection.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.e("TransferRepository", "Listen error on $collectionName", error)
                return@addSnapshotListener
            }

            snapshots?.documentChanges?.forEach { change ->
                if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                    try {
                        val receivedSignal = change.document.toObject(FirestoreSignal::class.java)
                        if (receivedSignal.sender != currentUser.uid) {
                            Log.d("TransferRepository", "Received signal on $collectionName from: ${receivedSignal.sender}")
                            onSignal(receivedSignal.sender, receivedSignal.signal)
                            // After processing, delete the individual signal document
                            change.document.reference.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("TransferRepository", "Error parsing signal on $collectionName", e)
                    }
                }
            }
        }
    }

    suspend fun clearAllSignalsForCurrentUser() {
        val currentUser = auth.currentUser ?: return
        clearSubcollection(WEBRTC_SIGNALS_COLLECTION, currentUser.uid)
        clearSubcollection(APP_EVENTS_COLLECTION, currentUser.uid)
    }

    private suspend fun clearSubcollection(collection: String, userId: String) {
        val subcollectionRef = db.collection(collection).document(userId).collection("signals")
        val snapshot = subcollectionRef.get().await()
        for (document in snapshot.documents) {
            document.reference.delete().await()
        }
    }
}
