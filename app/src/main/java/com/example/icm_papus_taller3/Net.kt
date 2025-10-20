package com.example.icm_papus_taller3

import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object Net {
    data class Resp(val code: Int, val body: String)

    fun httpJson(url: URL, method: String, json: String): Resp {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use(BufferedReader::readText)
        return Resp(code, text)
    }

    fun httpBin(
        url: URL,
        method: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String>
    ): Resp {
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            doOutput = true
        }
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            .bufferedReader().use(BufferedReader::readText)
        return Resp(code, text)
    }

    fun parseFirebaseError(resp: String): String {
        return try {
            val root = JSONObject(resp)
            val err = root.optJSONObject("error")
            when {
                err != null -> {
                    val code = err.optInt("code", 0)
                    val msg  = err.optString("message", resp)
                    if (code != 0) "HTTP $code: $msg" else msg
                }
                else -> resp
            }
        } catch (_: Exception) {
            resp
        }
    }
}
