package de.senad.iptv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 20)
        window.navigationBarColor = Color.rgb(12, 14, 20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(12, 14, 20))
        }
        val heading = TextView(this).apply {
            text = "IPTV_Senad"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }
        val subtitle = TextView(this).apply {
            text = "DEIN PERSÖNLICHER IPTV PLAYER"
            textSize = 12f
            setTextColor(Color.rgb(145, 155, 174))
        }
        streamInput = EditText(this).apply {
            hint = "M3U- oder Stream-URL eingeben"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setBackgroundColor(Color.rgb(30, 35, 46))
        }
        val playButton = Button(this).apply {
            text = "STREAM ABSPIELEN"
            setOnClickListener { playStream(streamInput.text.toString().trim()) }
        }
        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
        }
        val note = TextView(this).apply {
            text = "Füge eine M3U-Playlist oder eine direkte Stream-Adresse ein, für die du eine Nutzungsberechtigung hast."
            textSize = 13f
            setTextColor(Color.rgb(145, 155, 174))
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, 52))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, 28))
        root.addView(streamInput, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 18 })
        root.addView(playButton, LinearLayout.LayoutParams(-1, 56).apply { topMargin = 10 })
        root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 16 })
        root.addView(note, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 })
        setContentView(root)
    }

    private fun playStream(url: String) {
        if (url.isBlank() || !url.startsWith("https://")) {
            Toast.makeText(this, "Bitte eine gültige HTTPS-Stream-URL eingeben.", Toast.LENGTH_LONG).show()
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
        } catch (error: Exception) {
            Toast.makeText(this, "Stream konnte nicht gestartet werden: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
