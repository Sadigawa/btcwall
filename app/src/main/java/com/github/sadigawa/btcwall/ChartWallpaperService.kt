package com.github.sadigawa.btcwall

import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

class ChartWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ChartEngine()

    inner class ChartEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private var prices = DoubleArray(0)   // BTC, selected timeframe
        private var eth = DoubleArray(0)      // always 24h
        private var link = DoubleArray(0)     // always 24h
        private var status = "Loading…"
        private var lastFetch = 0L

        private var bgBitmap: Bitmap? = null
        private var canvasW = 0
        private var canvasH = 0

        // ---- look and feel: tweak these ----
        private val pctSize = 38f       // green/red % change text
        private val smallSize = 27f     // L / H labels
        private val riderBaseWidth = 132f   // roller coaster guy, in pixels (normal mode)
        private val green = Color.parseColor("#3DDC84")
        private val red = Color.parseColor("#FF5252")
        private val purple = Color.parseColor("#A26BFF")   // ETH
        private val blue = Color.parseColor("#4C82FF")     // LINK
        private val btcOrange = Color.parseColor("#F7931A")

        private val rider: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.roller) }
        private val riderPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        // "alt" draws the BTC / ETH / LINK names+prices in the corner: not bold, thinner
        private val alt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        private val pctBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = pctSize; typeface = Typeface.DEFAULT_BOLD
        }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9A9A9A"); textSize = smallSize
        }
        private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private val tick = object : Runnable {
            override fun run() {
                load()
                handler.postDelayed(this, Prefs.interval)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            val sp = PreferenceManager.getDefaultSharedPreferences(this@ChartWallpaperService)
            sp.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            if (key == Prefs.KEY_TIMEFRAME) { lastFetch = 0L; schedule(); load() }
            if (key == Prefs.KEY_BG_URI) { loadBackground(); draw() }
            if (key == Prefs.KEY_SHOW_BTC || key == Prefs.KEY_SHOW_ETH ||
                key == Prefs.KEY_SHOW_LINK || key == Prefs.KEY_TREND_ONLY || key == Prefs.KEY_CAR_LOCATION) draw()
        }

        private fun schedule() {
            handler.removeCallbacks(tick)
            val wait = (lastFetch + Prefs.interval - System.currentTimeMillis()).coerceAtLeast(0L)
            handler.postDelayed(tick, wait)
        }

        private fun fetchKraken(pair: String, intervalMin: Int, sinceSec: Long): DoubleArray {
            val url = URL("https://api.kraken.com/0/public/OHLC?pair=$pair&interval=$intervalMin&since=$sinceSec")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val result = JSONObject(body).getJSONObject("result")
            val candles = result.keys().asSequence()
                .map { result.opt(it) }.filterIsInstance<JSONArray>().first()
            return DoubleArray(candles.length()) { candles.getJSONArray(it).getString(4).toDouble() }
        }

        private fun load() {
            lastFetch = System.currentTimeMillis()
            val tf = Prefs.timeframe(applicationContext)
            Thread {
                try {
                    val btc = fetchKraken("XBTUSD", tf.intervalMin, tf.sinceSec())
                    handler.post { prices = btc; status = ""; draw() }
                } catch (e: Exception) {
                    handler.post { status = "Offline, retrying soon"; draw() }
                }
                val since24h = System.currentTimeMillis() / 1000 - 86400
                try {
                    val d = fetchKraken("ETHUSD", 5, since24h)
                    handler.post { eth = d; draw() }
                } catch (e: Exception) { }
                try {
                    val d = fetchKraken("LINKUSD", 5, since24h)
                    handler.post { link = d; draw() }
                } catch (e: Exception) { }
            }.start()
        }

        // ---------------- background image ----------------

        private fun loadBackground() {
            val uri = Prefs.bgUri(applicationContext)
            if (uri == null || canvasW == 0) { bgBitmap = null; return }
            try {
                contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    val bytes = stream.readBytes()
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var sample = 1
                    while (opts.outWidth / (sample * 2) >= canvasW && opts.outHeight / (sample * 2) >= canvasH) sample *= 2
                    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
                    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)
                    bgBitmap = raw?.let { coverCrop(it, canvasW, canvasH) }
                }
            } catch (e: Exception) {
                bgBitmap = null
            }
        }

        private fun coverCrop(src: Bitmap, w: Int, h: Int): Bitmap {
            val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
            val sw = (src.width * scale).toInt().coerceAtLeast(1)
            val sh = (src.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
            val x = ((sw - w) / 2).coerceAtLeast(0)
            val y = ((sh - h) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(scaled, x, y, w.coerceAtMost(sw), h.coerceAtMost(sh))
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) { schedule(); draw() } else handler.removeCallbacks(tick)
        }

        override fun onSurfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
            canvasW = w; canvasH = ht
            loadBackground()
            draw()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            PreferenceManager.getDefaultSharedPreferences(this@ChartWallpaperService)
                .unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        // ---------------- logos, drawn with shapes (no image files) ----------------

        private fun logoBtc(c: Canvas, cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = btcOrange })
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = r * 1.4f
                typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            val glyph = if (t.hasGlyph("\u20BF")) "\u20BF" else "B"
            c.drawText(glyph, cx, cy + r * 0.5f, t)
        }

        private fun logoEth(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            fun poly(vararg pts: Float) {
                val path = Path()
                path.moveTo(cx + pts[0] * r, cy + pts[1] * r)
                var i = 2
                while (i < pts.size) { path.lineTo(cx + pts[i] * r, cy + pts[i + 1] * r); i += 2 }
                path.close()
                c.drawPath(path, p)
            }
            poly(0f, -1f, 0.62f, 0.08f, 0f, 0.38f, -0.62f, 0.08f)
            poly(0f, 1f, 0.62f, 0.22f, 0f, 0.52f, -0.62f, 0.22f)
        }

        private fun logoLink(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            fun hex(rad: Float, col: Int) {
                val path = Path()
                for (k in 0..5) {
                    val a = Math.toRadians(-90.0 + 60.0 * k)
                    val x = cx + rad * cos(a).toFloat()
                    val y = cy + rad * sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = col })
            }
            hex(r, color)
            hex(r * 0.45f, Color.BLACK)
        }

        // ---------------- BTC / ETH / LINK rows (upper right) ----------------

        /** Right-aligned row: [logo] LABEL $price   +x.xx%.  kind: 0 = ETH, 1 = LINK, 2 = BTC */
        private fun drawRow(c: Canvas, kind: Int, label: String, d: DoubleArray, color: Int, right: Float, y: Float) {
            if (d.size < 2) return
            val lastV = d.last()
            val change = (lastV - d.first()) / d.first() * 100
            val pct = String.format(Locale.US, "%+.2f%%", change)
            val price = String.format(Locale.US, if (lastV >= 1000) "$%,.0f" else "$%,.2f", lastV)
            val pctPaint = Paint(pctBase).apply { this.color = if (change >= 0) green else red }
            val pctW = pctPaint.measureText(pct)
            c.drawText(pct, right - pctW, y, pctPaint)

            alt.color = color
            val text = "$label $price"
            val textX = right - pctW - 22f - alt.measureText(text)
            c.drawText(text, textX, y, alt)

            val r = 28f
            val cx = textX - 16f - r
            val cy = y - 23f
            when (kind) {
                0 -> logoEth(c, cx, cy, r, color)
                1 -> logoLink(c, cx, cy, r, color)
                else -> logoBtc(c, cx, cy, r)
            }
        }

        // ---------------- the roller coaster guy ----------------

        /** Sits on the line, tilted to match the local trend (down slope = nose down). */
        private fun drawRider(c: Canvas, xs: FloatArray, ys: FloatArray, width: Float) {
            val riderPosition = Prefs.carLocation(applicationContext) / 100f
            val idx = ((xs.size - 1) * riderPosition).toInt().coerceIn(0, xs.size - 1)
            val i1 = (idx - 20).coerceAtLeast(0)
            val i2 = (idx + 20).coerceAtMost(xs.size - 1)
            var mx = 0f; var my = 0f
            for (i in i1..i2) { mx += xs[i]; my += ys[i] }
            val n = (i2 - i1 + 1).toFloat()
            mx /= n; my /= n
            var num = 0f; var den = 0f
            for (i in i1..i2) { num += (xs[i] - mx) * (ys[i] - my); den += (xs[i] - mx) * (xs[i] - mx) }
            val angle = Math.toDegrees(atan(num / den.coerceAtLeast(1e-6f)).toDouble()).toFloat()
                .coerceIn(-90f, 90f)
            val s = width / rider.width
            c.save()
            c.translate(mx, my)
            c.rotate(angle)
            c.scale(s, s)
            c.drawBitmap(rider, -rider.width * 0.5f, -rider.height * 0.92f, riderPaint)
            c.restore()
        }

        /** Draws a simple roller-coaster rail: two parallel rails plus cross ties. */
        private fun drawRail(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / len; val uy = dy / len       // along the rail
            val px = -uy; val py = ux                  // perpendicular
            val gap = 10f
            val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; style = Paint.Style.STROKE
                strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(x1 + px * gap, y1 + py * gap, x2 + px * gap, y2 + py * gap, railPaint)
            c.drawLine(x1 - px * gap, y1 - py * gap, x2 - px * gap, y2 - py * gap, railPaint)
            val tiePaint = Paint(railPaint).apply { strokeWidth = 4f; alpha = 170 }
            val steps = (len / 26f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val cx = x1 + dx * t; val cy = y1 + dy * t
                c.drawLine(cx + px * (gap + 6f), cy + py * (gap + 6f),
                    cx - px * (gap + 6f), cy - py * (gap + 6f), tiePaint)
            }
        }

        private fun draw() {
            val c = surfaceHolder.lockCanvas() ?: return
            try {
                val w = c.width.toFloat()
                val h = c.height.toFloat()
                val bg = bgBitmap
                if (bg != null) c.drawBitmap(bg, 0f, 0f, bgPaint) else c.drawColor(Color.BLACK)

                val p = prices
                if (p.size < 2) {
                    c.drawText(status, w * 0.06f, h * 0.5f, small)
                    return
                }
                val left = w * 0.06f
                val right = w * 0.94f
                val top = h * 0.40f
                val bottom = h * 0.68f
                val lo = p.min()
                val hi = p.max()
                val range = if (hi - lo < 1e-9) 1.0 else hi - lo

                val lastV = p.last()
                val change = (lastV - p.first()) / p.first() * 100
                val trend = if (change >= 0) green else red
                line.color = trend

                val trendOnly = Prefs.trendOnly(applicationContext)
                val xs: FloatArray
                val ys: FloatArray
                if (trendOnly) {
                    // Two points only: 24h-ago (or period start) price on the left, now on the right.
                    val y0 = (bottom - (p.first() - lo) / range * (bottom - top)).toFloat()
                    val y1 = (bottom - (lastV - lo) / range * (bottom - top)).toFloat()
                    xs = floatArrayOf(left, right)
                    ys = floatArrayOf(y0, y1)
                    drawRail(c, xs[0], ys[0], xs[1], ys[1], trend)
                } else {
                    xs = FloatArray(p.size)
                    ys = FloatArray(p.size)
                    val path = Path()
                    for (i in p.indices) {
                        xs[i] = left + (right - left) * i / (p.size - 1)
                        ys[i] = (bottom - (p[i] - lo) / range * (bottom - top)).toFloat()
                        if (i == 0) path.moveTo(xs[i], ys[i]) else path.lineTo(xs[i], ys[i])
                    }
                    // With a custom background, skip the gradient fill and keep it a plain line.
                    if (bg == null) {
                        val area = Path(path).apply { lineTo(right, bottom); lineTo(left, bottom); close() }
                        fill.shader = LinearGradient(
                            0f, top, 0f, bottom,
                            (90 shl 24) or (trend and 0x00FFFFFF), trend and 0x00FFFFFF,
                            Shader.TileMode.CLAMP
                        )
                        c.drawPath(area, fill)
                    }
                    c.drawPath(path, line)
                }
                val riderWidth = if (trendOnly) riderBaseWidth * 4f else riderBaseWidth
                if (xs.size >= 2) drawRider(c, xs, ys, riderWidth)

                c.drawText(String.format(Locale.US, "L $%,.0f", lo), left, bottom + 40f, small)
                val hiText = String.format(Locale.US, "H $%,.0f", hi)
                c.drawText(hiText, right - small.measureText(hiText), bottom + 40f, small)
                if (status.isNotEmpty()) c.drawText(status, left, bottom + 80f, small)

                // BTC, ETH, LINK stacked in the upper right corner, each optional (ETH/LINK are always 24h)
                var rowY = h * 0.085f
                if (Prefs.showBtc(applicationContext)) { drawRow(c, 2, "BTC", p, btcOrange, right, rowY); rowY += 84f }
                if (Prefs.showEth(applicationContext)) { drawRow(c, 0, "ETH", eth, purple, right, rowY); rowY += 84f }
                if (Prefs.showLink(applicationContext)) { drawRow(c, 1, "LINK", link, blue, right, rowY) }
            } finally {
                surfaceHolder.unlockCanvasAndPost(c)
            }
        }
    }
}
