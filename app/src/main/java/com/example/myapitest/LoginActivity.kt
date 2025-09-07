package com.example.myapitest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException

import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var phoneNumberEditText: EditText
    private lateinit var phoneSignInButton: Button
    private lateinit var googleSignInButton: Button
    private lateinit var progressBar: ProgressBar

    private var storedVerificationId: String? = null

    companion object {
        private const val TAG = "LoginActivity"
        fun newIntent(context: Context) = Intent(context, LoginActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        auth = Firebase.auth
        credentialManager = CredentialManager.create(this)
        checkCurrentUser()
        setupClickListeners()
    }

    private fun initViews() {
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        phoneSignInButton = findViewById(R.id.phone_sign_in_button)
        googleSignInButton = findViewById(R.id.google_sign_in_button)
        progressBar = findViewById(R.id.progressBar)
    }


    private fun checkCurrentUser() {
        if (auth.currentUser != null) {
            goToHome()
        }
    }

    private fun setupClickListeners() {
        phoneSignInButton.setOnClickListener {
            val phoneNumber = phoneNumberEditText.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                startPhoneAuth(phoneNumber)
            } else {
                showToast("Digite um número de telefone")
            }
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        showLoading(true)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = credentialManager.getCredential(
                    request = request, context = this@LoginActivity
                )

                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "GetCredentialException: ${e.message}", e)

                if ("No credentials available".contains(e.message.toString(), true)) {
                    showToast("Nenhuma conta Google encontrada.")
                }

                showLoading(false)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                showLoading(false)

            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        when {
            credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
             try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                 firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
             }   catch (e: Exception) {
                     Log.e(TAG, "Error parsing Google ID token credential", e)
                     showToast("Erro ao processar credencial Google")
                     showLoading(false)
                }
            }
            else -> {
                Log.e(TAG, "Tipo de credencial desconhecido: ${credential.type}")
                showToast("Tipo de credencial desconhecido: ${credential.type}")
                showLoading(false)
            }
        }
    }


    private fun firebaseAuthWithGoogle(idToken: String?) {
        if (idToken.isNullOrEmpty()) {
            showToast("Token de acesso inválido")
            showLoading(false)
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Login com Google realizado com sucesso")
                    goToHome()
                } else {
                    Log.w(TAG, "Falha no login com Google", task.exception)
                    showToast("Falha na autenticação: ${task.exception?.message}")
                }
                showLoading(false)
            }
    }

    private fun startPhoneAuth(phoneNumber: String) {
        showLoading(true)

        val completePhoneNumber = if (phoneNumber.startsWith("+")) phoneNumber else "+55$phoneNumber"

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(completePhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d(TAG, "Verificação automática completada")
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w(TAG, "Falha na verificação: ${e.message}")
                    showToast("Falha na verificação: ${e.message}")
                    showLoading(false)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d(TAG, "Código enviado para: $completePhoneNumber")
                    storedVerificationId = verificationId
                    showLoading(false)

                    showCodeInputDialog(completePhoneNumber)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun showCodeInputDialog(phoneNumber: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_code_verification, null)
        val codeInput = dialogView.findViewById<EditText>(R.id.codeInput)

        AlertDialog.Builder(this)
            .setTitle("Verificação de Código")
            .setMessage("Digite o código de 6 dígitos enviado para:\n$phoneNumber")
            .setView(dialogView)
            .setPositiveButton("Verificar") { dialog, _ ->
                val code = codeInput.text.toString().trim()
                if (code.length == 6) {
                    verifyPhoneNumberWithCode(code)
                } else {
                    showToast("O código deve ter 6 dígitos")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun verifyPhoneNumberWithCode(code: String) {
        showLoading(true)

        storedVerificationId?.let { verificationId ->
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                signInWithPhoneAuthCredential(credential)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Credencial inválida", e)
                showToast("Código inválido ou expirado")
                showLoading(false)
            }
        } ?: run {
            Log.w(TAG, "Verification ID não encontrado")
            showToast("Sessão expirada. Tente novamente.")
            showLoading(false)
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Login com telefone realizado com sucesso")
                    goToHome()
                } else {
                    Log.w(TAG, "Falha no login com telefone", task.exception)
                    showToast("Falha na autenticação: ${task.exception?.message}")
                }
                showLoading(false)
            }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        phoneSignInButton.isEnabled = !show
        googleSignInButton.isEnabled = !show
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun goToHome() {
        startActivity(MainActivity.newIntent(this))
        finish()
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            goToHome()
        }
    }
}