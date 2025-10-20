package com.example.icm_papus_taller3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.URL
import java.net.URLEncoder

class RegisterActivity : AppCompatActivity() {

    private lateinit var imgPreview: ImageView
    private lateinit var btnPickImage: Button
    private lateinit var etNombre: EditText
    private lateinit var etApellido: EditText
    private lateinit var etCedula: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvCoords: TextView
    private lateinit var btnRegistrar: Button

    private var imageUri: Uri? = null
    private var lat: Double? = null
    private var lng: Double? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) { imageUri = uri; imgPreview.setImageURI(uri) }
    }

    private val requestLocPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> fetchLastLocation() }

    private val apiKey by lazy { getString(R.string.google_api_key) }
    private val dbUrl by lazy { getString(R.string.firebase_database_url).trimEnd('/') }
    private val bucket by lazy { getString(R.string.google_storage_bucket) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        imgPreview  = findViewById(R.id.imgPreview)
        btnPickImage= findViewById(R.id.btnPickImage)
        etNombre    = findViewById(R.id.etNombre)
        etApellido  = findViewById(R.id.etApellido)
        etCedula    = findViewById(R.id.etCedula)
        etEmail     = findViewById(R.id.etEmail)
        etPassword  = findViewById(R.id.etPassword)
        tvCoords    = findViewById(R.id.tvCoords)
        btnRegistrar= findViewById(R.id.btnRegistrar)

        btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        btnRegistrar.setOnClickListener { registrar() }
        requestLocationIfNeeded()
    }

    // ===== Ubicación nativa (sin Play Services) =====
    private fun requestLocationIfNeeded() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED || coarse != PackageManager.PERMISSION_GRANTED) {
            requestLocPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else fetchLastLocation()
    }

    private fun fetchLastLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = lm.getLastKnownLocation(p) ?: continue
            if (best == null || l.accuracy < best!!.accuracy) best = l
        }
        best?.let { lat = it.latitude; lng = it.longitude; tvCoords.text = "Lat: ${"%.6f".format(lat)}, Lng: ${"%.6f".format(lng)}" }
    }

    private fun registrar() {
        val nombre = etNombre.text.toString().trim()
        val apellido = etApellido.text.toString().trim()
        val cedula = etCedula.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val pass = etPassword.text.toString().trim()
        val img  = imageUri

        if (nombre.isEmpty() || apellido.isEmpty() || cedula.isEmpty()
            || email.isEmpty() || pass.length < 6 || img == null) {
            toast("Verifica campos e imagen (password ≥ 6).")
            return
        }

        Thread {
            try {
                // 1) Auth: SignUp (REST)
                val sUrl = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey")
                val signUpBody = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                    put("returnSecureToken", true)
                }.toString()
                val sResp = Net.httpJson(sUrl, "POST", signUpBody)
                if (sResp.code !in 200..299) {
                    val msg = Net.parseFirebaseError(sResp.body)
                    android.util.Log.e("SIGNUP", "resp=${sResp.body}")
                    runOnUiThread { toast("SignUp 400: $msg") }
                    return@Thread
                }
                val js = JSONObject(sResp.body)
                val idToken = js.getString("idToken")
                val uid     = js.getString("localId")

                // 2) Storage: subir foto ORIGINAL (alta resolución)
                val fotoUrl = uploadFullRes(bucket, uid, idToken, img)

                // 3) Realtime DB: guardar secundarios
                val userJson = JSONObject().apply {
                    put("uid", uid)
                    put("nombre", nombre)
                    put("apellido", apellido)
                    put("email", email)
                    put("numeroIdentificacion", cedula)
                    put("latitud", lat)
                    put("longitud", lng)
                    put("fotoUrl", fotoUrl)
                }.toString()
                val putUrl = URL("$dbUrl/users/$uid.json?auth=$idToken")
                val pResp = Net.httpJson(putUrl, "PUT", userJson)
                if (pResp.code !in 200..299) {
                    val msg = Net.parseFirebaseError(pResp.body)
                    android.util.Log.e("DB", "resp=${pResp.body}")
                    runOnUiThread { toast("DB 400: $msg") }
                    return@Thread
                }

                runOnUiThread { toast("Registro completo."); finish() }
            } catch (e: Exception) {
                runOnUiThread { toast("Error: ${e.message}") }
            }
        }.start()
    }

    // === Subida binaria a Firebase Storage (alta resolución) ===
    private fun uploadFullRes(bucket: String, uid: String, idToken: String, uri: Uri): String {
        val name = "profiles/$uid.jpg"
        val encoded = URLEncoder.encode(name, "UTF-8")
        val url = URL("https://firebasestorage.googleapis.com/v0/b/$bucket/o?name=$encoded&uploadType=media")

        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("No se pudo leer la imagen")
        val uResp = Net.httpBin(url, "POST", bytes, "image/jpeg", mapOf("Authorization" to "Firebase $idToken"))
        if (uResp.code !in 200..299) {
            val msg = Net.parseFirebaseError(uResp.body)
            android.util.Log.e("STORAGE", "resp=${uResp.body}")
            throw RuntimeException("Storage 400: $msg")
        }

        val meta = JSONObject(uResp.body)
        val token = meta.optString("downloadTokens")
        val storedName = meta.getString("name") // "profiles/uid.jpg"
        val storedEnc = URLEncoder.encode(storedName, "UTF-8")
        return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$storedEnc?alt=media&token=$token"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
