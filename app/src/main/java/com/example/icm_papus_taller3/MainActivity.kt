package com.example.icm_papus_taller3

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var tvUser: TextView? = null
    private var btnIrLogin: Button? = null
    private var btnIrRegistro: Button? = null
    // Quitamos el botón de logout: si existe en tu layout, lo ocultamos:
    private var btnLogout: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvUser        = findViewById(R.id.tvUser)
        btnIrLogin    = findViewById(R.id.btnIrLogin)
        btnIrRegistro = findViewById(R.id.btnIrRegistro)
        btnLogout     = findViewById(R.id.btnLogout) // podría no existir; si existe, la ocultamos

        btnLogout?.visibility = View.GONE

        btnIrLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        btnIrRegistro?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Si hay sesión, la pantalla principal es el MAPA
        if (AuthSession.uid(this) != null) {
            startActivity(Intent(this, OSMMapActivity::class.java))
            // finish() // si quieres quitar Main del back stack
        }
        refreshUser()
    }

    private fun refreshUser() {
        val uid = AuthSession.uid(this)
        val email = AuthSession.email(this)
        tvUser?.text = if (uid != null) "Autenticado: $email" else "(No autenticado)"
    }
}
