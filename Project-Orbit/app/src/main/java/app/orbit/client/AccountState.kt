package app.orbit.client

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import app.orbit.library.AccountApi
import app.orbit.library.Loadout
import java.io.File

/** ORBIT account (backend/ server). This is not a Microsoft account; Microsoft sign-in is not implemented. */
object AccountState {
    private lateinit var dir: File
    private lateinit var tokens: SecureStore
    private val _user = mutableStateOf<String?>(null)
    private val _server = mutableStateOf("")
    val user: String? get() = _user.value
    var server: String
        get() = _server.value
        set(v) { _server.value = v; runCatching { File(dir, "server.txt").writeText(v.trim()) } }

    fun init(ctx: Context) {
        dir = ctx.filesDir
        tokens = SecureStore(ctx, "account_token")
        _server.value = runCatching { File(dir, "server.txt").readText() }.getOrDefault("")
        _user.value = if (tokens.get() != null) runCatching { File(dir, "username.txt").readText() }.getOrNull() else null
    }

    private fun api() = AccountApi(server)
    private fun finish(token: String, name: String) {
        tokens.put(token); File(dir, "username.txt").writeText(name); _user.value = name
    }

    // The functions below do network I/O: call them off the main thread.
    fun signIn(name: String, password: String) { val r = api().signIn(name, password); finish(r.token, r.user.username) }
    fun signUp(name: String, password: String) { val r = api().signUp(name, password); finish(r.token, r.user.username) }
    fun signOut() { tokens.put(null); File(dir, "username.txt").delete(); _user.value = null }

    fun push() {
        val t = tokens.get() ?: error("Not signed in")
        val v = AppStore.viewer.value
        api().pushLoadout(t, Loadout(v.cosmetics, v.capeId, v.capeTexDataUri, v.petId))
    }

    /** Returns false if the server has nothing saved yet. */
    fun pull(): Boolean {
        val t = tokens.get() ?: error("Not signed in")
        val l = api().pullLoadout(t) ?: return false
        AppStore.update { it.copy(cosmetics = l.cosmetics, capeId = l.capeId, petId = l.petId) }
        l.capeData?.let { d -> runCatching { AppStore.setCustomCape(android.util.Base64.decode(d.substringAfter("base64,"), 0)) } }
        return true
    }
}
