package com.example.icm_papus_taller3

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class AvailableUsersService : Service() {

    companion object {
        const val ACTION_ADDED_OR_CHANGED = "availusers.added_or_changed"
        const val ACTION_REMOVED = "availusers.removed"

        const val EXTRA_UID = "uid"
        const val EXTRA_NAME = "name"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_FOTO = "fotoUrl"

        const val EXTRA_DB_URL = "dbUrl"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_SELF_UID = "selfUid"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val periodMs = 5000L

    private lateinit var dbUrl: String
    private lateinit var token: String
    private var selfUid: String = ""

    // cache actual de disponibles
    private val cache = ConcurrentHashMap<String, JSONObject>()

    private val task = object : Runnable {
        override fun run() {
            fetchAndDiff()
            handler.postDelayed(this, periodMs)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val u = intent?.getStringExtra(EXTRA_DB_URL) ?: return START_NOT_STICKY
        val t = intent.getStringExtra(EXTRA_TOKEN) ?: return START_NOT_STICKY
        dbUrl = u.trimEnd('/')
        token = t
        selfUid = intent.getStringExtra(EXTRA_SELF_UID) ?: ""

        // arranca el ciclo de polling
        handler.removeCallbacksAndMessages(null)
        handler.post(task)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fetchAndDiff() {
        try {
            val url = URL("$dbUrl/users.json?auth=$token&orderBy=%22status%22&equalTo=%22available%22")
            val (code, body) = httpGet(url)
            if (code !in 200..299) return

            val fresh = JSONObject(body) // objeto con uid -> user
            val freshKeys = mutableSetOf<String>()
            val it = fresh.keys()
            while (it.hasNext()) {
                val uid = it.next()
                if (uid == selfUid) continue
                freshKeys.add(uid)

                val u = fresh.getJSONObject(uid)
                val prev = cache[uid]
                cache[uid] = u

                if (prev == null) {
                    // NUEVO disponible -> toast + broadcast
                    val nombre = (u.optString("nombre","") + " " + u.optString("apellido","")).trim()
                    if (nombre.isNotEmpty()) {
                        handler.post {
                            Toast.makeText(this, "$nombre se conectó", Toast.LENGTH_SHORT).show()
                        }
                    }
                    emitAdded(uid, u)
                } else {
                    // cambio -> broadcast (por si actualiza foto/nombre)
                    if (!jsonEqualsShallow(u, prev)) emitAdded(uid, u)

                }
            }

            // removidos: los que estaban en cache y ya no vienen en fresh
            val toRemove = cache.keys.filter { it !in freshKeys }.toList()
            toRemove.forEach { uid ->
                cache.remove(uid)
                emitRemoved(uid)
            }

        } catch (_: Exception) {
            // silencio para no spamear
        }
    }

    private fun emitAdded(uid: String, u: JSONObject) {
        val nombre = (u.optString("nombre","") + " " + u.optString("apellido","")).trim()
        val i = Intent(ACTION_ADDED_OR_CHANGED).apply {
            putExtra(EXTRA_UID, uid)
            putExtra(EXTRA_NAME, if (nombre.isNotEmpty()) nombre else u.optString("email","Usuario"))
            putExtra(EXTRA_EMAIL, u.optString("email",""))
            putExtra(EXTRA_FOTO, u.optString("fotoUrl", null))
        }
        sendBroadcast(i)
    }

    private fun emitRemoved(uid: String) {
        val i = Intent(ACTION_REMOVED).apply { putExtra(EXTRA_UID, uid) }
        sendBroadcast(i)
    }

    private fun jsonEqualsShallow(a: JSONObject?, b: JSONObject?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false
        // Comparación rápida y suficiente para nuestro caso (campos planos)
        return a.toString() == b.toString()
    }


    private fun httpGet(url: URL): Pair<Int, String> {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15000
            readTimeout = 15000
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use(BufferedReader::readText)
        return code to text
    }
}
