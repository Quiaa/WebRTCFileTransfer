package com.example.webrtcfiletransfer.data.repository

import android.util.Log
import com.example.webrtcfiletransfer.data.model.CallSession
import com.example.webrtcfiletransfer.data.model.FileMetaData
import com.example.webrtcfiletransfer.data.model.IceCandidateModel
import com.example.webrtcfiletransfer.data.model.Sdp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object TransferRepository {

    private const val TAG = "TransferRepository"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private const val CALLS_COLLECTION = "calls"
    private const val CALLER_CANDIDATES = "callerCandidates"
    private const val CALLEE_CANDIDATES = "calleeCandidates"

    private var callListener: ListenerRegistration? = null

    // Listen for incoming calls for the current user.
    fun listenForIncomingCalls(): Flow<CallSession> = callbackFlow {
        val currentUser = auth.currentUser ?: return@callbackFlow
        val listener = db.collection(CALLS_COLLECTION)
            .whereEqualTo("calleeId", currentUser.uid)
            .whereEqualTo("status", "created")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Listen error", error)
                    close(error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val session = change.document.toObject(CallSession::class.java).apply {
                            callId = change.document.id
                        }
                        Log.d(TAG, "Incoming call detected: ${session.callId}")
                        trySend(session).isSuccess
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // Observe a specific call document for changes (e.g., answer added, status changed)
    fun observeCall(callId: String): Flow<CallSession> = callbackFlow {
        val docRef = db.collection(CALLS_COLLECTION).document(callId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val session = snapshot.toObject(CallSession::class.java)?.apply {
                    this.callId = snapshot.id
                }
                session?.let { trySend(it).isSuccess }
            }
        }
        awaitClose { listener.remove() }
    }

    fun observeIceCandidates(callId: String, isCaller: Boolean): Flow<IceCandidateModel> = callbackFlow {
        val collectionName = if (isCaller) CALLEE_CANDIDATES else CALLER_CANDIDATES
        val listener = db.collection(CALLS_COLLECTION).document(callId).collection(collectionName)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val candidate = change.document.toObject(IceCandidateModel::class.java)
                        trySend(candidate).isSuccess
                        // Delete candidate after processing
                        change.document.reference.delete()
                    }
                }
            }
        awaitClose { listener.remove() }
    }


    suspend fun initiateCall(calleeId: String, fileMetaData: FileMetaData, offer: Sdp): String {
        val currentUser = auth.currentUser!!
        val session = CallSession(
            callerId = currentUser.uid,
            calleeId = calleeId,
            status = "created",
            fileMetaData = fileMetaData,
            offer = offer
        )
        val docRef = db.collection(CALLS_COLLECTION).add(session).await()
        return docRef.id
    }

    suspend fun updateAnswer(callId: String, answer: Sdp) {
        db.collection(CALLS_COLLECTION).document(callId).update(
            mapOf("answer" to answer, "status" to "answered")
        ).await()
    }

    suspend fun addIceCandidate(callId: String, candidate: IceCandidateModel, isCaller: Boolean) {
        val collectionName = if (isCaller) CALLER_CANDIDATES else CALLEE_CANDIDATES
        db.collection(CALLS_COLLECTION).document(callId).collection(collectionName).add(candidate).await()
    }

    suspend fun updateStatus(callId: String, status: String) {
        db.collection(CALLS_COLLECTION).document(callId).update("status", status).await()
    }

    suspend fun deleteCall(callId: String) {
        val callRef = db.collection(CALLS_COLLECTION).document(callId)
        // Delete sub-collections first
        val callerCandidates = callRef.collection(CALLER_CANDIDATES).get().await()
        callerCandidates.documents.forEach { it.reference.delete() }
        val calleeCandidates = callRef.collection(CALLEE_CANDIDATES).get().await()
        calleeCandidates.documents.forEach { it.reference.delete() }
        // Delete the main document
        callRef.delete().await()
    }
}
