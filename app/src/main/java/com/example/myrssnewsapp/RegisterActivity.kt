package com.example.myrssnewsapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val usernameEditText = findViewById<EditText>(R.id.etUsername)
        val emailEditText = findViewById<EditText>(R.id.etEmail)
        val passwordEditText = findViewById<EditText>(R.id.etPassword)
        val repeatPasswordEditText = findViewById<EditText>(R.id.etRepeatPassword)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val registerGoogleButton = findViewById<Button>(R.id.btnRegisterGoogle)

        val emailFromGoogle = intent.getStringExtra("USER_EMAIL")
        if (emailFromGoogle != null) {
            emailEditText.setText(emailFromGoogle)
            emailEditText.isEnabled = false
        }

        googleSignInClient = GoogleSignIn.getClient(this, getGoogleSignInOptions())

        registerButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val repeatPassword = repeatPasswordEditText.text.toString().trim()

            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && password == repeatPassword) {
                register(username, email, password)
            } else {
                Toast.makeText(this, "Please fill in all fields correctly", Toast.LENGTH_SHORT).show()
            }
        }

        registerGoogleButton.setOnClickListener {
            username = usernameEditText.text.toString().trim()

            if (username.isNotEmpty()) {
                signInWithGoogle()
            } else {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun register(username: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid ?: return@addOnCompleteListener

                    val userMap = hashMapOf(
                        "username" to username,
                        "email" to email
                    )

                    firestore.collection("users").document(userId).set(userMap)
                        .addOnSuccessListener {
                            Log.d("Register", "User profile created successfully")
                            navigateToProfile()
                        }
                        .addOnFailureListener { e ->
                            Log.w("Register", "Error creating user profile", e)
                            Toast.makeText(this, "Registration Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.w("Register", "createUserWithEmail:failure", task.exception)
                    Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)!!
            Log.d("GoogleSignIn", "firebaseAuthWithGoogle:" + account.id)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w("GoogleSignIn", "Google sign in failed", e)
            Toast.makeText(this, "Google Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid ?: return@addOnCompleteListener

                    val userMap = hashMapOf(
                        "username" to username,
                        "email" to user.email
                    )

                    firestore.collection("users").document(userId).set(userMap)
                        .addOnSuccessListener {
                            Log.d("Register", "User profile created successfully")
                            navigateToProfile()
                        }
                        .addOnFailureListener { e ->
                            Log.w("Register", "Error creating user profile", e)
                            Toast.makeText(this, "Registration Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.w("GoogleSignIn", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Google Sign-In Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        finish() // Zamknięcie RegisterActivity, aby nie można było wrócić do ekranu rejestracji
    }

    private fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
}
