package de.senad.iptv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.util.concurrent.Executors

private data class TvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    val tvgId: String = "",
    val streamId: String = ""
)

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playlistUrlInput: EditText
    private lateinit var xtreamServerInput: EditText
    private lateinit var xtreamUserInput: EditText
    private lateinit var xtreamPasswordInput: EditText
    private lateinit var providerList: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var channelList: LinearLayout
    private lateinit var groupButton: Button
    private lateinit var favoritesButton: Button
    private var showFavoritesOnly = false
    private var selectedChannel: TvChannel? = null
    private val channels = mutableListOf<TvChannel>()
    private var groups: List<String> = listOf("Alle")
    private var selectedGroup = "Alle"
    private val io = Executors.newSingleThreadExecutor()

    private val bg = Color.rgb(12, 15, 22)
    private val panel = Color.rgb(23, 29, 40)
    private val muted = Color.rgb(145, 157, 177)
    private val accent = Color.rgb(73, 133, 230)
    private val prefs by lazy { getSharedPreferences("iptv_senad", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildTvFirstInterface()
        val saved = prefs.getString("playlist_text", null)
        if (!saved.isNullOrBlank()) {
            parsePlaylist(saved)
            statusText.text = "Gespeicherte Playlist: ${channels.size} Sender"
        }
    }

    private fun buildTvFirstInterface() {
        val wide = resources.configuration.screenWidthDp >= 700
        val root = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(bg)
        }

        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(panel)
        }
        sidebar.addView(label("IPTV_Senad", 23f, Color.WHITE, true), match(58))
        sidebar.addView(label("LIVE TV", 11f, muted, true), match(34))
        addNavButton(sidebar, "⌂  Übersicht", true)
        addNavButton(sidebar, "▣  Live-TV", false)
        addNavButton(sidebar, "▤  EPG / Programm", false) { showEpgForSelectedChannel() }
        addNavButton(sidebar, "★  Favoriten", false)
        addNavButton(sidebar, "＋  Anbieter", false)
        addNavButton(sidebar, "⚙  Einstellungen", false)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(4), dp(4))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(10))
        }
        header.addView(label("Fernsehen. Einfach. Übersichtlich.", 24f, Color.WHITE, true), match(42))
        header.addView(label("Dein persönlicher IPTV-Player", 13f, muted), match(26))
        content.addView(header, matchWrap())

        playlistUrlInput = EditText(this).apply {
            hint = "HTTPS-Adresse deiner M3U/M3U8-Playlist"
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 14f
            singleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(panel)
            isFocusable = true
        }
        content.addView(playlistUrlInput, match(52))

        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val urlButton = Button(this).apply {
            text = "URL LADEN"
            isAllCaps = false
            setOnClickListener { loadRemotePlaylist(playlistUrlInput.text.toString().trim()) }
        }
        val fileButton = Button(this).apply {
            text = "DATEI ÖFFNEN"
            isAllCaps = false
            setOnClickListener { openPlaylistFile() }
        }
        importRow.addView(urlButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        importRow.addView(fileButton, LinearLayout.LayoutParams(0, dp(48), 1f))

        content.addView(importRow, match(56))
        content.addView(label("XTREAM-CODES-ANBIETER", 12f, muted, true), match(30))
        val xtreamForm = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        xtreamServerInput = credentialInput("Server-URL (HTTPS), z. B. https://anbieter.de:8080")
        xtreamUserInput = credentialInput("Benutzername")
        xtreamPasswordInput = credentialInput("Passwort", true)
        xtreamForm.addView(xtreamServerInput, match(44))
        xtreamForm.addView(xtreamUserInput, match(44))
        xtreamForm.addView(xtreamPasswordInput, match(44))
        val xtreamButton = Button(this).apply {
            text = "XTREAM-ANBIETER VERBINDEN"
            isAllCaps = false
            setOnClickListener {
                connectXtream(xtreamServerInput.text.toString().trim(),
                    xtreamUserInput.text.toString().trim(), xtreamPasswordInput.text.toString())
            }
        }
        xtreamForm.addView(xtreamButton, match(46))
        content.addView(xtreamForm, matchWrap())
        content.addView(label("GESPEICHERTE ANBIETER", 12f, muted, true), match(30))
        providerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(providerList, matchWrap())
        refreshProviderList()

        val body = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val channelsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        channelsPanel.addView(label("SENDERLISTE", 12f, muted, true), match(28))
        groupButton = Button(this).apply {
            text = "Gruppe: Alle"
            isAllCaps = false
            setOnClickListener { nextGroup() }
        }
        channelsPanel.addView(groupButton, match(46))
        favoritesButton = Button(this).apply {
            text = "★ Favoriten anzeigen"
            isAllCaps = false
            setOnClickListener {
                showFavoritesOnly = !showFavoritesOnly
                text = if (showFavoritesOnly) "Alle Sender anzeigen" else "★ Favoriten anzeigen"
                renderChannels()
            }
        }
        channelsPanel.addView(favoritesButton, match(42))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        channelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(channelList, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        channelsPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        statusText = label("Bereit – importiere deine eigene M3U-Playlist", 12f, muted)
        channelsPanel.addView(statusText, matchWrap().apply { topMargin = dp(8) })

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            contentDescription = "Videoplayer"
        }
        if (wide) {
            body.addView(channelsPanel, LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(10) })
            body.addView(playerView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            body.addView(channelsPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)).apply { bottomMargin = dp(8) })
            body.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        content.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        if (wide) {
            root.addView(sidebar, LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(12) })
            root.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            val nav = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(panel)
            }
            listOf("Home", "Live", "EPG", "Favoriten", "Anbieter").forEach { title ->
                val b = Button(this).apply {
                    text = title
                    textSize = 10f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    setBackgroundColor(panel)
                    setOnClickListener {
                        if (title == "EPG") showEpgForSelectedChannel()
                        else if (title == "Favoriten") {
                            showFavoritesOnly = !showFavoritesOnly
                            renderChannels()
                        }
                    }
                }
                nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            root.addView(nav, match(52))
            root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        renderChannels()
    }

    private fun addNavButton(parent: LinearLayout, title: String, selected: Boolean, action: (() -> Unit)? = null) {
        val button = Button(this).apply {
            text = title
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            isAllCaps = false
            isFocusable = true
            setTextColor(if (selected) Color.WHITE else muted)
            setBackgroundColor(if (selected) accent else panel)
            setOnClickListener {
                if (action != null) action()
                else Toast.makeText(this@MainActivity, "$title – Bereich wird weiter ausgebaut", Toast.LENGTH_SHORT).show()
            }
        }
        parent.addView(button, match(50).apply { bottomMargin = dp(4) })
    }


    private fun credentialInput(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        setHintTextColor(muted)
        setTextColor(Color.WHITE)
        textSize = 13f
        singleLine = true
        setPadding(dp(10), 0, dp(10), 0)
        setBackgroundColor(bg)
        if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun connectXtream(serverInput: String, username: String, password: String) {
        val server = serverInput.trimEnd('/')
        val parsed = runCatching { URL(server) }.getOrNull()
        if (parsed == null || parsed.protocol != "https" || parsed.host.isBlank() || username.isBlank() || password.isBlank()) {
            showError("Bitte gültige HTTPS-Serveradresse, Benutzername und Passwort eingeben.")
            return
        }
        statusText.text = "Xtream-Anbieter wird geprüft …"
        io.execute {
            try {
                val base = "$server/player_api.php?username=${enc(username)}&password=${enc(password)}"
                val account = requestJson(base)
                val userInfo = account.optJSONObject("user_info") ?: throw IllegalStateException("Ungültige Xtream-API-Antwort")
                if (userInfo.optInt("auth", 0) != 1) throw IllegalStateException("Anmeldung abgelehnt – Zugangsdaten prüfen")
                val categories = requestJson("$base&action=get_live_categories").optJSONArray("categories") ?: JSONArray()
                val streams = requestJson("$base&action=get_live_streams")
                val imported = mutableListOf<TvChannel>()
                for (i in 0 until streams.length()) {
                    val item = streams.optJSONObject(i) ?: continue
                    val id = item.optString("stream_id")
                    if (id.isBlank() || id == "null") continue
                    val catId = item.optString("category_id")
                    var group = "Weitere"
                    for (j in 0 until categories.length()) {
                        val cat = categories.optJSONObject(j) ?: continue
                        if (cat.optString("category_id") == catId) { group = cat.optString("category_name", "Weitere"); break }
                    }
                    val name = item.optString("name").ifBlank { "Sender $id" }
                    val logo = item.optString("stream_icon").takeUnless { it == "null" }.orEmpty()
                    val streamUrl = "$server/live/${encPath(username)}/${encPath(password)}/$id.ts"
                    imported.add(TvChannel(name, streamUrl, group, logo, item.optString("epg_channel_id"), id))
                }
                if (imported.isEmpty()) throw IllegalStateException("Keine Live-TV-Sender vom Anbieter erhalten")
                val encrypted = encryptPassword(password)
                val profiles = JSONArray(prefs.getString("xtream_profiles", "[]"))
                val profile = JSONObject().put("name", parsed.host).put("server", server)
                    .put("username", username).put("encrypted_password", encrypted)
                var replaced = false
                for (i in 0 until profiles.length()) {
                    val old = profiles.optJSONObject(i) ?: continue
                    if (old.optString("server") == server && old.optString("username") == username) {
                        profiles.put(i, profile); replaced = true; break
                    }
                }
                if (!replaced) profiles.put(profile)
                prefs.edit().putString("xtream_profiles", profiles.toString())
                    .putString("active_xtream_server", server)
                    .putString("active_xtream_user", username)
                    .putString("active_xtream_password", encrypted).apply()
                runOnUiThread {
                    channels.clear()
                    channels.addAll(imported)
                    groups = listOf("Alle") + channels.map { it.group }.distinct().sorted()
                    selectedGroup = "Alle"
                    groupButton.text = "Gruppe: Alle"
                    renderChannels()
                    xtreamPasswordInput.text.clear()
                    statusText.text = "Xtream verbunden – ${channels.size} Live-Sender · Anbieter verschlüsselt gespeichert"
                    refreshProviderList()
                    Toast.makeText(this, "${channels.size} Sender geladen", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { showError("Xtream-Verbindung fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}") }
            }
        }
    }


    private fun decryptPassword(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.size <= 12) throw IllegalStateException("Gespeichertes Anbieterprofil ist beschädigt")
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("IPTV_Senad_Xtream_Key", null) as? SecretKey
            ?: throw IllegalStateException("Verschlüsselungsschlüssel nicht gefunden – Anbieter bitte neu anmelden")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun refreshProviderList() {
        if (!::providerList.isInitialized) return
        providerList.removeAllViews()
        val profiles = JSONArray(prefs.getString("xtream_profiles", "[]"))
        if (profiles.length() == 0) {
            providerList.addView(label("Noch kein Anbieter gespeichert.", 12f, muted), match(38))
            return
        }
        for (i in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(i) ?: continue
            val server = profile.optString("server")
            val username = profile.optString("username")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(panel)
                setPadding(dp(6), 0, dp(6), 0)
            }
            val use = Button(this).apply {
                text = "${profile.optString("name")} · $username"
                isAllCaps = false
                textSize = 11f
                setOnClickListener {
                    try {
                        val password = decryptPassword(profile.optString("encrypted_password"))
                        connectXtream(server, username, password)
                    } catch (e: Exception) {
                        showError("Anbieter konnte nicht geladen werden: ${e.message}")
                    }
                }
            }
            val delete = Button(this).apply {
                text = "Löschen"
                isAllCaps = false
                textSize = 10f
                setOnClickListener {
                    val updated = JSONArray()
                    for (j in 0 until profiles.length()) {
                        if (j != i) profiles.optJSONObject(j)?.let { updated.put(it) }
                    }
                    prefs.edit().putString("xtream_profiles", updated.toString()).apply()
                    refreshProviderList()
                    Toast.makeText(this@MainActivity, "Anbieterprofil gelöscht", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(use, LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(delete, LinearLayout.LayoutParams(dp(90), dp(48)))
            providerList.addView(row, matchWrap().apply { bottomMargin = dp(4) })
        }
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "IPTV_Senad/1.0")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun encPath(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun encryptPassword(value: String): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "IPTV_Senad_Xtream_Key"
        val key = if (store.containsAlias(alias)) store.getKey(alias, null) as SecretKey else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generator.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun openPlaylistFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_PLAYLIST)
    }

    @Deprecated("Use Activity Result APIs in a future UI refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PLAYLIST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            io.execute {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Datei konnte nicht gelesen werden")
                    runOnUiThread { acceptPlaylist(text, "Datei importiert") }
                } catch (e: Exception) {
                    runOnUiThread { showError("Datei konnte nicht geladen werden: ${e.message}") }
                }
            }
        }
    }

    private fun loadRemotePlaylist(address: String) {
        if (!address.startsWith("https://", ignoreCase = true)) {
            showError("Bitte eine HTTPS-M3U-Adresse eingeben.")
            return
        }
        statusText.text = "Playlist wird geladen …"
        io.execute {
            try {
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "IPTV_Senad/1.0")
                }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                runOnUiThread { acceptPlaylist(text, "Playlist von URL geladen") }
            } catch (e: Exception) {
                runOnUiThread { showError("Playlist konnte nicht geladen werden: ${e.message}") }
            }
        }
    }

    private fun acceptPlaylist(text: String, message: String) {
        if (!text.trimStart().startsWith("#EXTM3U")) {
            showError("Keine gültige M3U-Playlist: #EXTM3U-Kopf fehlt.")
            return
        }
        parsePlaylist(text)
        if (channels.isEmpty()) {
            showError("Keine abspielbaren Sender in der Playlist gefunden.")
            return
        }
        prefs.edit().putString("playlist_text", text).apply()
        statusText.text = "$message – ${channels.size} Sender"
        Toast.makeText(this, "${channels.size} Sender importiert", Toast.LENGTH_SHORT).show()
    }

    private fun parsePlaylist(text: String) {
        channels.clear()
        var pendingName: String? = null
        var pendingGroup = "Weitere"
        var pendingLogo = ""
        var pendingId = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    val comma = line.indexOf(',')
                    val attributes = if (comma >= 0) line.substring(0, comma) else line
                    pendingName = if (comma >= 0) line.substring(comma + 1).trim().ifBlank { "Unbekannter Sender" } else "Unbekannter Sender"
                    pendingGroup = attribute(attributes, "group-title").ifBlank { "Weitere" }
                    pendingLogo = attribute(attributes, "tvg-logo")
                    pendingId = attribute(attributes, "tvg-id")
                    val tvgName = attribute(attributes, "tvg-name")
                    if (pendingName == "Unbekannter Sender" && tvgName.isNotBlank()) pendingName = tvgName
                }
                line.isNotBlank() && !line.startsWith("#") && pendingName != null -> {
                    if (line.startsWith("https://", true)) {
                        channels.add(TvChannel(pendingName!!, line, pendingGroup, pendingLogo, pendingId))
                    }
                    pendingName = null
                    pendingGroup = "Weitere"
                    pendingLogo = ""
                    pendingId = ""
                }
            }
        }
        groups = listOf("Alle") + channels.map { it.group }.distinct().sorted()
        if (selectedGroup !in groups) selectedGroup = "Alle"
        renderChannels()
    }

    private fun attribute(source: String, key: String): String {
        val regex = Regex("""(?:^|\s)$key="([^"]*)"""", RegexOption.IGNORE_CASE)
        return regex.find(source)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun nextGroup() {
        val index = groups.indexOf(selectedGroup).coerceAtLeast(0)
        selectedGroup = groups[(index + 1) % groups.size]
        groupButton.text = "Gruppe: $selectedGroup"
        renderChannels()
    }

    private fun renderChannels() {
        if (!::channelList.isInitialized) return
        channelList.removeAllViews()
        val favoriteUrls = prefs.getStringSet("favorite_urls", emptySet()) ?: emptySet()
        val visible = channels.filter {
            (selectedGroup == "Alle" || it.group == selectedGroup) &&
                (!showFavoritesOnly || favoriteUrls.contains(it.url))
        }
        if (visible.isEmpty()) {
            channelList.addView(label("Noch keine Sender vorhanden.\nImportiere eine M3U-Datei oder HTTPS-Playlist.", 13f, muted))
            return
        }
        visible.forEachIndexed { index, channel ->
            val item = TextView(this).apply {
                text = "${index + 1}.  ${channel.name}\n      ${channel.group}"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(10), dp(9), dp(8), dp(9))
                isFocusable = true
                isClickable = true
                setBackgroundColor(if (index % 2 == 0) Color.rgb(29, 37, 51) else panel)
                setOnClickListener { playChannel(channel) }
                setOnLongClickListener {
                    val favorites = (prefs.getStringSet("favorite_urls", emptySet()) ?: emptySet()).toMutableSet()
                    val added = if (favorites.contains(channel.url)) {
                        favorites.remove(channel.url)
                        false
                    } else {
                        favorites.add(channel.url)
                        true
                    }
                    prefs.edit().putStringSet("favorite_urls", favorites).apply()
                    Toast.makeText(this@MainActivity,
                        if (added) "Zu Favoriten hinzugefügt" else "Aus Favoriten entfernt",
                        Toast.LENGTH_SHORT).show()
                    renderChannels()
                    true
                }
            }
            channelList.addView(item, match(58).apply { bottomMargin = dp(4) })
        }
    }

    private fun playChannel(channel: TvChannel) {
        selectedChannel = channel
        try {
            val exo = player ?: ExoPlayer.Builder(this).build().also {
                player = it
                playerView.player = it
            }
            exo.setMediaItem(MediaItem.fromUri(channel.url))
            exo.prepare()
            exo.play()
            statusText.text = "Jetzt: ${channel.name}"
        } catch (e: Exception) {
            showError("Sender konnte nicht gestartet werden: ${e.message}")
        }
    }


    private fun showEpgForSelectedChannel() {
        val channel = selectedChannel
        if (channel == null) {
            Toast.makeText(this, "Bitte zuerst einen Sender auswählen.", Toast.LENGTH_SHORT).show()
            return
        }
        if (channel.streamId.isBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("EPG – \${channel.name}")
                .setMessage("Für diesen M3U-Sender ist noch keine XMLTV-EPG-Quelle eingerichtet. Bei Xtream-Anbietern kann das Programm direkt geladen werden.")
                .setPositiveButton("OK", null).show()
            return
        }
        val server = prefs.getString("active_xtream_server", null)
        val username = prefs.getString("active_xtream_user", null)
        val encrypted = prefs.getString("active_xtream_password", null)
        if (server.isNullOrBlank() || username.isNullOrBlank() || encrypted.isNullOrBlank()) {
            showError("Bitte zuerst einen Xtream-Anbieter verbinden.")
            return
        }
        val loading = android.app.AlertDialog.Builder(this)
            .setTitle("EPG – \${channel.name}")
            .setMessage("Programm wird geladen …")
            .setCancelable(false).create()
        loading.show()
        io.execute {
            try {
                val password = decryptPassword(encrypted)
                val address = "$server/player_api.php?username=\${enc(username)}&password=\${enc(password)}&action=get_short_epg&stream_id=\${enc(channel.streamId)}&limit=10"
                val response = requestJson(address)
                val listings = response.optJSONArray("epg_listings") ?: JSONArray()
                val text = if (listings.length() == 0) {
                    "Der Anbieter hat für diesen Sender aktuell keine EPG-Daten geliefert."
                } else {
                    (0 until listings.length()).mapNotNull { i ->
                        val item = listings.optJSONObject(i) ?: return@mapNotNull null
                        val title = decodeEpg(item.optString("title")).ifBlank { "Sendung ohne Titel" }
                        val start = formatEpgTime(item.optString("start"))
                        val end = formatEpgTime(item.optString("end"))
                        val description = decodeEpg(item.optString("description"))
                        buildString {
                            append(if (start.isNotBlank()) "$start – $end" else "Sendezeit unbekannt")
                            append("\n$title")
                            if (description.isNotBlank()) append("\n\${description.take(260)}")
                        }
                    }.joinToString("\n\n──────────────\n\n")
                }
                runOnUiThread {
                    loading.dismiss()
                    android.app.AlertDialog.Builder(this)
                        .setTitle("EPG – \${channel.name}")
                        .setMessage(text)
                        .setPositiveButton("Schließen", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading.dismiss()
                    showError("EPG konnte nicht geladen werden: \${e.message ?: "Unbekannter Fehler"}")
                }
            }
        }
    }

    private fun decodeEpg(value: String): String {
        if (value.isBlank() || value == "null") return ""
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun formatEpgTime(value: String): String {
        if (value.isBlank() || value == "null") return ""
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")
        for (pattern in formats) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.GERMANY).parse(value)
            }.getOrNull()
            if (parsed != null) return java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(parsed)
        }
        return value
    }

    private fun showError(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun match(height: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_PLAYLIST = 4102
    }
}
