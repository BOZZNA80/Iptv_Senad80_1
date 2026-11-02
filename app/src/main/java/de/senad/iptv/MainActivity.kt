package de.senad.iptv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import java.util.concurrent.Executors

private data class TvChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    val tvgId: String = ""
)

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playlistUrlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var channelList: LinearLayout
    private lateinit var groupButton: Button
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
        addNavButton(sidebar, "▤  EPG / Programm", false)
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
                }
                nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            root.addView(nav, match(52))
            root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        renderChannels()
    }

    private fun addNavButton(parent: LinearLayout, title: String, selected: Boolean) {
        val button = Button(this).apply {
            text = title
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            isAllCaps = false
            isFocusable = true
            setTextColor(if (selected) Color.WHITE else muted)
            setBackgroundColor(if (selected) accent else panel)
            setOnClickListener { Toast.makeText(this@MainActivity, "$title – folgt in einem späteren Ausbauschritt", Toast.LENGTH_SHORT).show() }
        }
        parent.addView(button, match(50).apply { bottomMargin = dp(4) })
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
        val regex = Regex("""(?:^|\\s)$key="([^"]*)"""", RegexOption.IGNORE_CASE)
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
        val visible = channels.filter { selectedGroup == "Alle" || it.group == selectedGroup }
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
            }
            channelList.addView(item, match(58).apply { bottomMargin = dp(4) })
        }
    }

    private fun playChannel(channel: TvChannel) {
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
