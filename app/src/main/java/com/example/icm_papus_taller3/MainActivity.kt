package com.example.icm_papus_taller3

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var tvUser: TextView? = null
    private var btnIrLogin: Button? = null
    private var btnIrRegistro: Button? = null
    private var btnLogout: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias (si algún id no existe en tu layout, no crashea)
        tvUser        = findViewById(R.id.tvUser)
        btnIrLogin    = findViewById(R.id.btnIrLogin)
        btnIrRegistro = findViewById(R.id.btnIrRegistro)
        btnLogout     = findViewById(R.id.btnLogout)

        btnIrLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnIrRegistro?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogout?.setOnClickListener {
            AuthSession.clear(this)
            Toast.makeText(this, "Sesión cerrada.", Toast.LENGTH_SHORT).show()
            refreshUser()
        }
    }

    override fun onResume() {
        super.onResume()

        // Si ya está autenticado, la pantalla principal es el mapa
        if (AuthSession.uid(this) != null) {
            startActivity(Intent(this, OSMMapActivity::class.java))
            // Si quieres que al volver atrás no regrese a Main:
            // finish()
        }

        refreshUser()
    }

    private fun refreshUser() {
        val uid = AuthSession.uid(this)
        val email = AuthSession.email(this)
        tvUser?.text = if (uid != null) "Autenticado: $email" else "(No autenticado)"
    }
}
