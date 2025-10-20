package com.example.icm_papus_taller3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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

class OSMMapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var userPoint: GeoPoint? = null
    private val poiMarkers = mutableListOf<Marker>()
    private var userMarker: Marker? = null

    private val USER_ZOOM = 15.0

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        fetchLastLocation()
        centerAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargar config de OSMDroid + user agent antes de inflar layout
        val ctx = applicationContext
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(ctx, prefs)
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_osmmap)
        map = findViewById(R.id.osmMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // 1) Cargar POIs desde assets/locations.json y pintarlos
        val points = loadLocationsFromAssets()
        addPoiMarkers(points)

        // 2) Ubicación del usuario y centrado
        requestLocationIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    // ---------- JSON ----------
    private data class GeoItem(val name: String, val lat: Double, val lng: Double)

    private fun loadLocationsFromAssets(): List<GeoItem> {
        return try {
            val text = assets.open("locations.json").bufferedReader().use(BufferedReader::readText)
            val root = JSONObject(text)
            val items = mutableListOf<GeoItem>()

            // locationsArray: [{name, latitude, longitude}, ...]
            if (root.has("locationsArray")) {
                val arr = root.getJSONArray("locationsArray")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(
                        GeoItem(
                            name = o.optString("name", "POI ${i + 1}"),
                            lat = o.getDouble("latitude"),
                            lng = o.getDouble("longitude")
                        )
                    )
                }
            }
            // locations: {"0": {...}, "1": {...}, ...}
            if (root.has("locations")) {
                val obj = root.getJSONObject("locations")
                val keys = obj.keys()
                var idx = 1
                while (keys.hasNext()) {
                    val k = keys.next()
                    val o = obj.getJSONObject(k)
                    items.add(
                        GeoItem(
                            name = o.optString("name", "POI ${idx++}"),
                            lat = o.getDouble("latitude"),
                            lng = o.getDouble("longitude")
                        )
                    )
                }
            }
            items.take(5) // solo 5 POIs
        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo locations.json: ${e.message}", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    // ---------- Marcadores ----------
    private fun addPoiMarkers(items: List<GeoItem>) {
        poiMarkers.forEach { map.overlays.remove(it) }
        poiMarkers.clear()

        items.forEach { item ->
            val m = Marker(map).apply {
                position = GeoPoint(item.lat, item.lng)
                title = item.name
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
            reqPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
                centerOnUser() // centramos con zoom medio apenas tengamos posición
            }
        } catch (_: Exception) { /* no-op */ }

        addUserMarkerIfAny()
    }

    private fun centerOnUser() {
        val up = userPoint ?: return
        map.controller.setZoom(USER_ZOOM)
        map.controller.setCenter(up)
    }

    // ---------- Vista ----------
    private fun centerAll() {
        // Si tenemos ubicación, prioriza centrar en el usuario con zoom medio
        if (userPoint != null) {
            centerOnUser()
            return
        }
        // Si no hay ubicación, encuadra los POIs
        val points = mutableListOf<GeoPoint>()
        poiMarkers.forEach { points.add(it.position) }

        if (points.isNotEmpty()) {
            val bbox: BoundingBox = BoundingBox.fromGeoPoints(points)
            map.zoomToBoundingBox(bbox, true)
        } else {
            // Fallback: Bogotá
            map.controller.setZoom(12.0)
            map.controller.setCenter(GeoPoint(4.65, -74.06))
        }
    }
}
