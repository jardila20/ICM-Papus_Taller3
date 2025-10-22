package com.example.icm_papus_taller3

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class OSMMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var userPoint: GeoPoint? = null
    private val poiMarkers = mutableListOf<Marker>()
    private var userMarker: Marker? = null

    private val USER_ZOOM = 15.0
    private val dbUrl by lazy { getString(R.string.firebase_database_url).trimEnd('/') }

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        fetchLastLocation()
        centerAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Config OSMDroid + user-agent antes del layout
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_osmmap)
        map = findViewById(R.id.osmMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Botones “menú” desde el layout
        findViewById<Button>(R.id.btnToggleStatus).setOnClickListener { toggleStatus() }
        findViewById<Button>(R.id.btnLogout).setOnClickListener { doLogout() }
        findViewById<Button>(R.id.btnOpenUsers).setOnClickListener {
            startActivity(Intent(this, AvailableUsersActivity::class.java))
        }


        // 1) POIs desde assets/locations.json
        val points = loadLocationsFromAssets()
        addPoiMarkers(points)

        // 2) Ubicación + centrado
        requestLocationIfNeeded()
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }

    // ---------- JSON ----------
    private data class GeoItem(val name: String, val lat: Double, val lng: Double)

    private fun loadLocationsFromAssets(): List<GeoItem> {
        return try {
            val text = assets.open("locations.json").bufferedReader().use(BufferedReader::readText)
            val root = JSONObject(text)
            val items = mutableListOf<GeoItem>()

            if (root.has("locationsArray")) {
                val arr = root.getJSONArray("locationsArray")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(GeoItem(o.optString("name","POI ${i+1}"), o.getDouble("latitude"), o.getDouble("longitude")))
                }
            }
            if (root.has("locations")) {
                val obj = root.getJSONObject("locations")
                val keys = obj.keys()
                var idx = 1
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = obj.getJSONObject(k)
                    items.add(GeoItem(o.optString("name","POI ${idx++}"), o.getDouble("latitude"), o.getDouble("longitude")))
                }
            }
            items.take(5)
        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo locations.json: ${e.message}", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    // ---------- Marcadores ----------
    private fun addPoiMarkers(items: List<GeoItem>) {
        poiMarkers.forEach { map.overlays.remove(it) }
        poiMarkers.clear()

        items.forEach { it ->
            val m = Marker(map).apply {
                position = GeoPoint(it.lat, it.lng)
                title = it.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            poiMarkers.add(m)
            map.overlays.add(m)
        }
        map.invalidate()
    }

    private fun addUserMarkerIfAny() {
        userMarker?.let { map.overlays.remove(it) }
        val up = userPoint ?: return
        userMarker = Marker(map).apply {
            position = up
            title = "Mi ubicación"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(userMarker)
        map.invalidate()
    }

    // ---------- Ubicación ----------
    private fun requestLocationIfNeeded() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED || coarse != PackageManager.PERMISSION_GRANTED) {
            reqPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            fetchLastLocation()
            centerAll()
        }
    }

    private fun fetchLastLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.accuracy < best!!.accuracy) best = l
            }
            best?.let { loc ->
                userPoint = GeoPoint(loc.latitude, loc.longitude)
                centerOnUser()
            }
        } catch (_: Exception) { /* no-op */ }

        addUserMarkerIfAny()
    }

    private fun centerOnUser() {
        val up = userPoint ?: return
        map.controller.setZoom(15.0)
        map.controller.setCenter(up)
    }

    private fun centerAll() {
        if (userPoint != null) { centerOnUser(); return }
        val pts = mutableListOf<GeoPoint>()
        poiMarkers.forEach { pts.add(it.position) }
        if (pts.isNotEmpty()) {
            val bbox: BoundingBox = BoundingBox.fromGeoPoints(pts)
            map.zoomToBoundingBox(bbox, true)
        } else {
            map.controller.setZoom(12.0)
            map.controller.setCenter(GeoPoint(4.65, -74.06)) // Bogotá fallback
        }
    }

    // ---------- Estado & Logout ----------


    private fun toggleStatus() {
        val uid = AuthSession.uid(this)
        val token = AuthSession.token(this)
        val dbUrl = getString(R.string.firebase_database_url).trimEnd('/')
        if (uid == null || token == null) {
            Toast.makeText(this, "No hay sesión.", Toast.LENGTH_SHORT).show()
            return
        }

        // alterna estado local para decidir el siguiente
        val sp = getSharedPreferences("presence", MODE_PRIVATE)
        val current = sp.getString("status", "available") ?: "available"
        val next = if (current == "available") "offline" else "available"

        Thread {
            try {
                val url = java.net.URL("$dbUrl/users/$uid.json?auth=$token")
                val body = org.json.JSONObject().put("status", next).toString()
                val (code, resp) = httpPatchJson(url, body)
                if (code in 200..299) {
                    sp.edit().putString("status", next).apply()
                    runOnUiThread {
                        // toast breve que pediste cuando queda "available"
                        if (next == "available") {
                            val who = AuthSession.email(this) ?: "Usuario"
                            Toast.makeText(this, "$who disponible", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Estado: offline", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "No se pudo actualizar estado: $resp", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }





    private fun doLogout() {
        AuthSession.clear(this)
        Toast.makeText(this, "Sesión cerrada.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ---------- PATCH helper ----------
    private fun httpPatchJson(url: URL, json: String): Pair<Int, String> {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use(BufferedReader::readText)
        return code to text
    }
}
