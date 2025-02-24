package com.example.todolist.signin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.todolist.MainActivity
import com.example.todolist.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.calendar.CalendarScopes

class SignInActivity : AppCompatActivity() {

    companion object {
        private var currentAccount: GoogleSignInAccount? = null

        fun setCurrentAccount(account: GoogleSignInAccount?) {
            currentAccount = account
        }

        fun getLastSignedInAccount(): GoogleSignInAccount? {
            return currentAccount
        }

        private const val RC_SIGN_IN = 9001
    }

    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()


        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<View>(R.id.sign_in_button).setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent: Intent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrEmpty()) {
                Log.d("SignInActivity", "Compte connecté avec email: ${account.email}")

                // Stocker le compte dans les SharedPreferences
                getSharedPreferences("my_prefs", Context.MODE_PRIVATE).edit().apply {
                    putString("user_email", account.email)
                    putString("user_display_name", account.displayName)
                    apply()
                }

                setCurrentAccount(account)  // Stocker dans le companion object

                if (GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR))) {
                    Log.d("SignInActivity", "Permissions calendrier OK")
                    updateUI(account)
                } else {
                    Log.d("SignInActivity", "Demande de permissions calendrier")
                    GoogleSignIn.requestPermissions(
                        this,
                        RC_SIGN_IN,
                        account,
                        Scope(CalendarScopes.CALENDAR)
                    )
                }
            } else {
                Log.e("SignInActivity", "L'email du compte est null ou vide")
                updateUI(null)
            }
        } catch (e: ApiException) {
            Log.e("SignInActivity", "Erreur de connexion: ${e.message}", e)
            Toast.makeText(this, "La connexion a échoué. Erreur : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            updateUI(null)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("USER_NAME", account.displayName)
                putExtra("USER_EMAIL", account.email)
            }
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "La connexion a échoué. Réessayez.", Toast.LENGTH_SHORT).show()
        }
    }
}
