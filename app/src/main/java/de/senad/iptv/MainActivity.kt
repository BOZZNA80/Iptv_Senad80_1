package de.senad.iptv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var streamInput: EditText
    private lateinit var statusText: TextView

    private val bg = Color.rgb(12, 15, 22)
    private val panel = Color.rgb(23, 29, 40)
    private val muted = Color.rgb(145, 157, 177)
    private val accent = Color.rgb(73, 133, 230)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildTvFirstInterface()
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
        val brand = label("IPTV_Senad", 23f, Color.WHITE, true)
        sidebar.addView(brand, match(58))
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
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        header.addView(label("Fernsehen. Einfach. Übersichtlich.", 24f, Color.WHITE, true), match(42))
        header.addView(label("Dein persönlicher IPTV-Player", 13f, muted), match(28))
        content.addView(header, matchWrap())

        val importRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        streamInput = EditText(this).apply {
            hint = "M3U- oder HTTPS-Stream-URL"
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 14f
            singleLine = true
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(panel)
            isFocusable = true
        }
        importRow.addView(streamInput, LinearLayout.LayoutParams(0, dp(52), 1f))
        val playButton = Button(this).apply {
            text = "ABSPIELEN"
            isFocusable = true
            setOnClickListener { playStream(streamInput.text.toString().trim()) }
        }
        importRow.addView(playButton, LinearLayout.LayoutParams(dp(132), dp(52)).apply { leftMargin = dp(8) })
        content.addView(importRow, match(60))

        val body = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val channels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
        }
        channels.addView(label("SENDERLISTE", 12f, muted, true), match(32))
        channels.addView(label("Alle Sender", 16f, Color.WHITE, true), match(42))
        channels.addView(label("Noch keine Sender vorhanden", 15f, Color.WHITE), match(44))
        channels.addView(label("Importiere deine eigene M3U-Playlist oder verbinde einen Anbieter.", 12f, muted), matchWrap())
        statusText = label("Bereit für deine Playlist", 12f, muted)
        channels.addView(statusText, matchWrap().apply { topMargin = dp(16) })

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            contentDescription = "Videoplayer"
        }
        if (wide) {
            body.addView(channels, LinearLayout.LayoutParams(dp(250), 0, 1f).apply { rightMargin = dp(10) })
            body.addView(playerView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            body.addView(channels, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)).apply { bottomMargin = dp(8) })
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
            addCompactNav(nav, "Home")
            addCompactNav(nav, "Live")
            addCompactNav(nav, "EPG")
            addCompactNav(nav, "Favoriten")
            addCompactNav(nav, "Anbieter")
            root.addView(nav, match(52))
            root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
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
            setOnClickListener { Toast.makeText(this@MainActivity, "$title – wird im nächsten Ausbauschritt aktiviert", Toast.LENGTH_SHORT).show() }
        }
        parent.addView(button, match(50).apply { bottomMargin = dp(4) })
    }

    private fun addCompactNav(parent: LinearLayout, title: String) {
        val button = Button(this).apply {
            text = title
            textSize = 10f
            isAllCaps = false
            isFocusable = true
            setTextColor(Color.WHITE)
            setBackgroundColor(panel)
        }
        parent.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
    }

    private fun playStream(url: String) {
        if (url.isBlank() || !url.startsWith("https://")) {
            Toast.makeText(this, "Bitte eine gültige HTTPS-Stream-Adresse eingeben.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val exo = player ?: ExoPlayer.Builder(this).build().also {
                player = it
                playerView.player = it
            }
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.play()
            statusText.text = "Wiedergabe gestartet"
        } catch (error: Exception) {
            Toast.makeText(this, "Stream konnte nicht gestartet werden: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
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
}
