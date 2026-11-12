package app.orbit.library

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiUser(val id: Long, val username: String)
class AuthResult(val token: String, val user: ApiUser)
class Loadout(val cosmetics: List<String>, val capeId: String, val capeData: String?, val petId: String?)

/** Client for the backend/ server. HTTPS only; the password is sent once and never stored. */
class AccountApi(base: String) {
    private val base = base.trim().trimEnd('/')

    private fun call(method: String, path: String, token: String?, body: JSONObject?): JSONObject {
        require(base.startsWith("https://", true)) { "The server URL must start with https://" }
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 15000; c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) error(json?.optString("error")?.ifEmpty { null } ?: "Server error ($code)")
            return json ?: JSONObject()
        } finally { c.disconnect() }
    }

    private fun parseAuth(j: JSONObject) = j.getJSONObject("user").let { AuthResult(j.getString("token"), ApiUser(it.getLong("id"), it.getString("username"))) }

    fun signUp(u: String, p: String) = parseAuth(call("POST", "/auth/signup", null, JSONObject().put("username", u).put("password", p)))
    fun signIn(u: String, p: String) = parseAuth(call("POST", "/auth/login", null, JSONObject().put("username", u).put("password", p)))

    fun pushLoadout(token: String, l: Loadout) {
        call("PUT", "/me/loadout", token, JSONObject().put("loadout", JSONObject()
            .put("cosmetics", JSONArray(l.cosmetics)).put("capeId", l.capeId)
            .put("capeData", l.capeData ?: JSONObject.NULL).put("petId", l.petId ?: JSONObject.NULL)))
    }

    fun pullLoadout(token: String): Loadout? {
        val o = call("GET", "/me/loadout", token, null).optJSONObject("loadout") ?: return null
        val c = o.getJSONArray("cosmetics")
        return Loadout((0 until c.length()).map { c.getString(it) }, o.getString("capeId"),
            o.optString("capeData").ifEmpty { null }, o.optString("petId").ifEmpty { null })
    }
}
