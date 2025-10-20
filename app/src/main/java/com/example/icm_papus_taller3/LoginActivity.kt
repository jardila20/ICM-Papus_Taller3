package com.example.icm_papus_taller3

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.URL

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    private val apiKey by lazy { getString(R.string.google_api_key) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString().trim()
            if (!isValid(email, pass)) return@setOnClickListener

            Thread {
                try {
                    val url = URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey")
                    val body = JSONObject().apply {
                        put("email", email)
                        put("password", pass)
                        put("returnSecureToken", true)
                    }.toString()

                    val (code, resp) = Net.httpJson(url, "POST", body)
                    if (code in 200..299) {
                        val js = JSONObject(resp)
                        AuthSession.save(this, js.getString("idToken"), js.getString("localId"), email)
                        runOnUiThread {
                            Toast.makeText(this, "Sesión iniciada.", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        val msg = Net.parseFirebaseError(resp)
                        android.util.Log.e("LOGIN", "resp=$resp")
                        runOnUiThread {
                            Toast.makeText(this, "Login 400: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }

    private fun isValid(email: String, pass: String): Boolean {
        if (!email.contains("@") || !email.contains(".")) {
            Toast.makeText(this,"Email inválido",Toast.LENGTH_SHORT).show(); return false
        }
        if (pass.length < 6) {
            Toast.makeText(this,"Password mínimo 6",Toast.LENGTH_SHORT).show(); return false
        }
        return true
    }
}
