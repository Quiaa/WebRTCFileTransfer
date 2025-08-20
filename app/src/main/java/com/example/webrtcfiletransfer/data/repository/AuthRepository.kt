package com.example.webrtcfiletransfer.data.repository

import com.example.webrtcfiletransfer.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Function to handle user sign-up.
    suspend fun signUp(username: String, email: String, password: String) {
        // Create user with email and password in Firebase Authentication.
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user ?: throw Exception("User creation failed.")

        // Create a User object with the provided details.
        val user = User(uid = firebaseUser.uid, username = username, email = email)

        // Save the user object to the "users" collection in Firestore.
        // The document ID will be the user's UID for easy retrieval.
        db.collection("users").document(firebaseUser.uid).set(user).await()
    }

    // Function to handle user login.
    suspend fun login(email: String, password: String) {
        // Sign in the user with email and password.
        auth.signInWithEmailAndPassword(email, password).await()
    }
}
