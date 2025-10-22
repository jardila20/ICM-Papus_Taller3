package com.example.icm_papus_taller3

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class AvailableUsersActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private val items = mutableListOf<UItem>()
    private lateinit var adapter: UserAdapter

    private val dbUrl by lazy { getString(R.string.firebase_database_url).trimEnd('/') }
    private val token by lazy { AuthSession.token(this) }
    private val myUid by lazy { AuthSession.uid(this) }

    data class UItem(
        val uid: String,
        val name: String,
        val email: String,
        val fotoUrl: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_available_users)

        list = findViewById(R.id.listUsers)
        adapter = UserAdapter(this, items)
        list.adapter = adapter

        if (token == null || myUid == null) {
            Toast.makeText(this, "No hay sesión.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // GET inicial (filtrado) — lo que ya tenías funcionando
        loadUsers()
    }

    private fun loadUsers() {
        Thread {
            try {
                // Importante: comillas URL-encoded para cumplir reglas
                val url = URL("$dbUrl/users.json?auth=$token&orderBy=%22status%22&equalTo=%22available%22")
                val (code, body) = httpGet(url)
                if (code !in 200..299) {
                    runOnUiThread {
                        Toast.makeText(this, "DB error: $body", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                val obj = JSONObject(body)
                val fresh = mutableListOf<UItem>()
                val it = obj.keys()
                while (it.hasNext()) {
                    val uid = it.next()
                    if (uid == myUid) continue
                    val u = obj.getJSONObject(uid)
                    val nombre = (u.optString("nombre","") + " " + u.optString("apellido","")).trim()
                    val display = if (nombre.isNotEmpty()) nombre else u.optString("email","(sin nombre)")
                    fresh.add(
                        UItem(
                            uid = uid,
                            name = display,
                            email = u.optString("email",""),
                            fotoUrl = u.optString("fotoUrl", null)
                        )
                    )
                }
                runOnUiThread {
                    items.clear()
                    items.addAll(fresh)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // -------- HTTP helper --------
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

    // -------- Adapter personalizado --------
    private class UserAdapter(
        val ctx: Context,
        val data: MutableList<UItem>
    ) : BaseAdapter() {

        override fun getCount() = data.size
        override fun getItem(position: Int) = data[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = convertView ?: android.view.LayoutInflater.from(ctx).inflate(R.layout.row_user, parent, false)
            val item = getItem(position)

            val img = view.findViewById<ImageView>(R.id.imgUser)
            val name = view.findViewById<TextView>(R.id.txtName)
            val email = view.findViewById<TextView>(R.id.txtEmail)
            val btn = view.findViewById<Button>(R.id.btnVer)

            name.text = item.name
            email.text = item.email

            img.setImageResource(android.R.drawable.sym_def_app_icon)
            item.fotoUrl?.let { urlStr ->
                Thread {
                    try {
                        val c = URL(urlStr).openConnection() as HttpURLConnection
                        c.connect()
                        val bmp = BitmapFactory.decodeStream(c.inputStream)
                        (ctx as AppCompatActivity).runOnUiThread {
                            if (bmp != null) img.setImageBitmap(bmp)
                        }
                    } catch (_: Exception) {}
                }.start()
            }

            btn.setOnClickListener {
                val i = Intent(ctx, TrackUserActivity::class.java)
                i.putExtra("targetUid", item.uid)
                i.putExtra("targetName", item.name)
                i.putExtra("targetFoto", item.fotoUrl ?: "")
                ctx.startActivity(i)
            }

            return view
        }
    }
}
