package de.playlisteq.app.spotify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import de.playlisteq.app.core.LiveState
import de.playlisteq.app.model.SpotifyContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Spotify Web API (PKCE, ohne Client-Secret).
 * Liefert den echten Playlist-Namen der aktuellen Wiedergabe.
 * Login-Rückleitung über einen kleinen lokalen Server auf 127.0.0.1 (von Spotify erlaubt).
 */
object SpotifyClient {
    const val PORT = 8766
    const val REDIRECT_URI = "http://127.0.0.1:$PORT/callback"
    private const val SCOPES =
        "user-read-currently-playing user-read-playback-state playlist-read-private playlist-read-collaborative"
    private const val TAG = "SpotifyClient"

    private lateinit var prefs: SharedPreferences
    private val playlistNames = HashMap<String, String?>()
    @Volatile private var server: ServerSocket? = null

    /** Wird nach erfolgreichem Login erhöht, damit sofort neu abgefragt wird. */
    val connectedTick = MutableStateFlow(0)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
        LiveState.spotifyStatus.value = if (isConnected()) "Verbunden" else "Nicht verbunden"
    }

    fun isConnected(): Boolean = prefs.getString("refresh", null) != null

    fun disconnect() {
        prefs.edit().clear().apply()
        LiveState.spotify.value = null
        LiveState.spotifyStatus.value = "Nicht verbunden"
    }

    // ---------------- Login ----------------

    fun startLogin(context: Context, clientId: String) {
        if (clientId.isBlank()) {
            LiveState.spotifyStatus.value = "Bitte zuerst die Client-ID eintragen."
            return
        }
        val verifier = randomString(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val state = randomString(16)

        runCatching { server?.close() }
        thread(name = "spotify-login") { runLoopback(clientId, verifier, state) }

        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun runLoopback(clientId: String, verifier: String, state: String) {
        try {
            ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1")).use { ss ->
                server = ss
                ss.soTimeout = 5 * 60 * 1000
                LiveState.spotifyStatus.value = "Warte auf Anmeldung im Browser…"
                while (true) {
                    val done = ss.accept().use { sock -> handleCallback(sock, clientId, verifier, state) }
                    if (done) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Login-Server beendet", e)
            if (!isConnected()) LiveState.spotifyStatus.value = "Anmeldung abgebrochen oder Zeit abgelaufen."
        } finally {
            server = null
        }
    }

    private fun handleCallback(sock: Socket, clientId: String, verifier: String, state: String): Boolean {
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val requestLine = reader.readLine() ?: return false
        val path = requestLine.split(" ").getOrNull(1) ?: return false
        if (!path.startsWith("/callback")) {
            respond(sock, 404, "Not found")
            return false
        }
        val uri = Uri.parse("http://127.0.0.1$path")
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val ok = error == null && code != null && uri.getQueryParameter("state") == state &&
            exchangeCode(clientId, code, verifier)

        val msg = if (ok) "Spotify ist verbunden. Du kannst jetzt zu PlaylistEQ zurückkehren."
        else "Anmeldung fehlgeschlagen (${error ?: "unbekannter Fehler"}). Bitte in PlaylistEQ erneut versuchen."
        respond(
            sock, 200,
            "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width'>" +
                "<title>PlaylistEQ</title></head><body style='font-family:sans-serif;padding:2em;text-align:center'>" +
                "<h2>PlaylistEQ</h2><p>$msg</p></body></html>",
        )
        LiveState.spotifyStatus.value = if (ok) "Verbunden" else "Anmeldung fehlgeschlagen: ${error ?: "Token-Fehler"}"
        if (ok) connectedTick.value = connectedTick.value + 1
        return true
    }

    private fun respond(sock: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Not Found"}\r\n" +
            "Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        sock.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }

    private fun exchangeCode(clientId: String, code: String, verifier: String): Boolean {
        val (status, body) = request(
            "https://accounts.spotify.com/api/token", "POST",
            form = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
        )
        if (status != 200 || body == null) return false
        saveTokens(JSONObject(body), clientId)
        return true
    }

    private fun saveTokens(json: JSONObject, clientId: String) {
        val e = prefs.edit()
            .putString("access", json.getString("access_token"))
            .putLong("expires", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
            .putString("clientId", clientId)
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { e.putString("refresh", it) }
        e.apply()
    }

    private fun accessToken(forceRefresh: Boolean = false): String? {
        val access = prefs.getString("access", null)
        if (!forceRefresh && access != null && prefs.getLong("expires", 0) > System.currentTimeMillis() + 60_000) {
            return access
        }
        val refresh = prefs.getString("refresh", null) ?: return null
        val clientId = prefs.getString("clientId", null) ?: return null
        val (status, body) = request(
            "https://accounts.spotify.com/api/token", "POST",
            form = mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId),
        )
        if (status != 200 || body == null) {
            if (status == 400) {
                disconnect()
                LiveState.spotifyStatus.value = "Abgemeldet – bitte neu verbinden."
            }
            return null
        }
        saveTokens(JSONObject(body), clientId)
        return prefs.getString("access", null)
    }

    // ---------------- Abfrage ----------------

    /** Aktueller Wiedergabekontext (Playlist, Album, …) – blockierend, nur im Hintergrund aufrufen. */
    fun fetchContext(): SpotifyContext? {
        var token = accessToken() ?: return null
        var (status, body) = request("https://api.spotify.com/v1/me/player/currently-playing", token = token)
        if (status == 401) {
            token = accessToken(forceRefresh = true) ?: return null
            val retry = request("https://api.spotify.com/v1/me/player/currently-playing", token = token)
            status = retry.first
            body = retry.second
        }
        if (status == 204 || body.isNullOrBlank()) return null
        if (status != 200) return null
        val ctx = JSONObject(body).optJSONObject("context") ?: return SpotifyContext(null, null, null)
        val type = ctx.optString("type").ifBlank { null }
        val uri = ctx.optString("uri").ifBlank { null }
        val name = if (type == "playlist" && uri != null) playlistName(uri, token) else null
        return SpotifyContext(type, uri, name)
    }

    private fun playlistName(uri: String, token: String): String? {
        if (playlistNames.containsKey(uri)) return playlistNames[uri]
        val id = uri.substringAfterLast(":")
        val (status, body) = request("https://api.spotify.com/v1/playlists/$id?fields=name", token = token)
        // Spotify-eigene Playlists (z. B. "Daily Mix") liefern hier oft 404 – dann bleibt nur die URI.
        val name = if (status == 200 && body != null) JSONObject(body).optString("name").ifBlank { null } else null
        playlistNames[uri] = name
        return name
    }

    // ---------------- HTTP ----------------

    private fun request(
        url: String,
        method: String = "GET",
        token: String? = null,
        form: Map<String, String>? = null,
    ): Pair<Int, String?> {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = 8000
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val payload = form.entries.joinToString("&") {
                    "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                }
                c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            status to stream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP-Fehler $url", e)
            -1 to null
        } finally {
            c.disconnect()
        }
    }

    private fun randomString(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return String(CharArray(len) { chars[rnd.nextInt(chars.length)] })
    }
}
