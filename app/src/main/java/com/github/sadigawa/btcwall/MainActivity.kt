package com.github.sadigawa.btcwall

import androidx.activity.ComponentActivity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var timeframeRow: LinearLayout

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            Prefs.setBgUri(this, uri.toString())
            showStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        fun heading(text: String) = TextView(this).apply {
            this.text = text
            textSize = 18f
            setPadding(0, dp(18), 0, dp(6))
        }

        status = TextView(this).apply { textSize = 15f }
        col.addView(status)

        col.addView(Button(this).apply {
            text = "Set as wallpaper"
            setOnClickListener {
                val set = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ChartWallpaperService::class.java)
                )
                try { startActivity(set) } catch (e: Exception) {
                    startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                }
            }
        })

        col.addView(heading("Chart time frame"))
        timeframeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = Prefs.Timeframe.values().size.toFloat()
        }
        col.addView(timeframeRow)
        buildTimeframeButtons()

        col.addView(heading("Background"))
        col.addView(Button(this).apply {
            text = "Choose a background photo"
            setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        })
        col.addView(Button(this).apply {
            text = "Use plain black (default)"
            setOnClickListener { Prefs.setBgUri(this@MainActivity, null); showStatus() }
        })

        col.addView(heading("Show in corner"))
        col.addView(CheckBox(this).apply {
            text = "BTC"; isChecked = Prefs.showBtc(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setBool(this@MainActivity, Prefs.KEY_SHOW_BTC, checked) }
        })
        col.addView(CheckBox(this).apply {
            text = "ETH"; isChecked = Prefs.showEth(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setBool(this@MainActivity, Prefs.KEY_SHOW_ETH, checked) }
        })
        col.addView(CheckBox(this).apply {
            text = "LINK"; isChecked = Prefs.showLink(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setBool(this@MainActivity, Prefs.KEY_SHOW_LINK, checked) }
        })

        col.addView(heading("Chart style"))
        col.addView(CheckBox(this).apply {
            text = "Trend line only (roller coaster rail, start-to-now)"
            isChecked = Prefs.trendOnly(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setBool(this@MainActivity, Prefs.KEY_TREND_ONLY, checked) }
        })

        col.addView(heading("Car location"))
        val carLabel = TextView(this).apply { textSize = 14f }
        col.addView(carLabel)
        val carSeek = SeekBar(this).apply {
            max = 100
            progress = Prefs.carLocation(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    carLabel.text = "Car location: $value%  (0% = far left, 100% = far right)"
                    if (fromUser) Prefs.setCarLocation(this@MainActivity, value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        carLabel.text = "Car location: ${carSeek.progress}%  (0% = far left, 100% = far right)"
        col.addView(carSeek)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun buildTimeframeButtons() {
        timeframeRow.removeAllViews()
        val current = Prefs.timeframe(this)
        for (tf in Prefs.Timeframe.values()) {
            timeframeRow.addView(Button(this).apply {
                text = tf.label
                textSize = 12f
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (tf == current) setBackgroundColor(Color.parseColor("#F7931A"))
                setOnClickListener {
                    Prefs.setTimeframe(this@MainActivity, tf)
                    buildTimeframeButtons()
                    showStatus()
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        showStatus()
    }

    private fun showStatus() {
        val tf = Prefs.timeframe(this)
        val bg = Prefs.bgUri(this)
        status.text = "Time frame: ${tf.label}" +
            "\nBackground: " + (if (bg == null) "plain black" else "custom photo") +
            "\n\nChanges apply the next time the wallpaper refreshes (within 5 minutes), or right away if you reopen it."
        status.gravity = Gravity.START
    }
}
