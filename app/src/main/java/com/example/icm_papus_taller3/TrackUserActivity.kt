package com.example.icm_papus_taller3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class TrackUserActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvDist: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val pollMs = 5000L

    private val dbUrl by lazy { getString(R.string.firebase_database_url).trimEnd('/') }
    private val token by lazy { AuthSession.token(this) }

    private var mePoint: GeoPoint? = null
    private var targetPoint: GeoPoint? = null

    private var meMarker: Marker? = null
    private var targetMarker: Marker? = null
    private var line: Polyline? = null

    private var targetUid: String = ""
    private var targetName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid config
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_track_user)
        map = findViewById(R.id.trackMap)
        tvDist = findViewById(R.id.txtDistance)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        targetUid = intent.getStringExtra("targetUid") ?: ""
        targetName = intent.getStringExtra("targetName") ?: "Usuario"

        // Tu ubicación (seguidor)
        fetchMyLocation()

        // Empieza polling del target
        startPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }

    private fun startPolling() {
        pollOnce()
        handler.postDelayed(poller, pollMs)
    }

    private fun stopPolling() {
        handler.removeCallbacksAndMessages(null)
    }

    private val poller = object : Runnable {
        override fun run() {
            pollOnce()
            handler.postDelayed(this, pollMs)
        }
    }

    private fun pollOnce() {
        if (token == null || targetUid.isEmpty()) return
        Thread {
            try {
                val url = URL("$dbUrl/users/$targetUid.json?auth=$token")
                val (code, body) = httpGet(url)
                if (code !in 200..299) return@Thread
                val obj = JSONObject(body)
                val lat = obj.optDouble("latitud", Double.NaN).let {
                    if (it.isNaN()) obj.optDouble("lat", Double.NaN) else it
                }
                val lng = obj.optDouble("longitud", Double.NaN).let {
                    if (it.isNaN()) obj.optDouble("lng", Double.NaN) else it
                }
                if (!lat.isNaN() && !lng.isNaN()) {
                    val gp = GeoPoint(lat, lng)
                    runOnUiThread { updateTarget(gp) }
                }
            } catch (_: Exception) { /* no-op */ }
        }.start()
    }

    private fun updateTarget(gp: GeoPoint) {
        targetPoint = gp
        if (targetMarker == null) {
            targetMarker = Marker(map).apply {
                position = gp
                title = targetName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(targetMarker)
        } else {
            targetMarker!!.position = gp
        }
        redrawLineAndDistance()
    }

    // ------- Tu ubicación (seguidor) -------
    private fun fetchMyLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val f = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val c = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (f != PackageManager.PERMISSION_GRANTED && c != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de ubicación no concedido.", Toast.LENGTH_SHORT).show()
            return
        }
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = lm.getLastKnownLocation(p) ?: continue
            if (best == null || l.accuracy < best!!.accuracy) best = l
        }
        best?.let { loc ->
            mePoint = GeoPoint(loc.latitude, loc.longitude)
            if (meMarker == null) {
                meMarker = Marker(map).apply {
                    position = mePoint
                    title = "Yo"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(meMarker)
            } else {
                meMarker!!.position = mePoint
            }
            // Centrado inicial
            map.controller.setZoom(15.0)
            map.controller.setCenter(mePoint)
        }
    }

    private fun redrawLineAndDistance() {
        val a = mePoint ?: return
        val b = targetPoint ?: return

        // Línea recta
        if (line == null) {
            line = Polyline().apply { setPoints(listOf(a, b)) }
            map.overlays.add(line)
        } else {
            line!!.setPoints(listOf(a, b))
        }

        // Distancia (metros)
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude, b.latitude, b.longitude, results
        )
        val m = results[0]
        val text = if (m < 1000) {
            "Distancia: ${m.roundToInt()} m"
        } else {
            "Distancia: ${(m / 1000.0).let { (it * 10).roundToInt() / 10.0 }} km"
        }
        tvDist.text = text

        map.invalidate()
    }

    // ------- REST GET -------
    private fun httpGet(url: URL): Pair<Int, String> {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use(BufferedReader::readText)
        return code to text
    }
}
