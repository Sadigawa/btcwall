package com.github.sadigawa.btcwall

import android.content.Context
import androidx.preference.PreferenceManager

/** Shared settings between MainActivity and the wallpaper engine. */
object Prefs {
    const val KEY_TIMEFRAME = "timeframe"
    const val KEY_BG_URI = "bg_uri"
    const val KEY_SHOW_BTC = "show_btc"
    const val KEY_SHOW_ETH = "show_eth"
    const val KEY_SHOW_LINK = "show_link"
    const val KEY_TREND_ONLY = "trend_only"
    const val KEY_CAR_LOCATION = "car_location"   // 0..100, where the rider sits along the chart

    enum class Timeframe(val label: String, val intervalMin: Int, val sinceDays: Long) {
        H24("24H", 5, 1),
        D7("7D", 15, 7),
        M1("1M", 240, 30),
        M3("3M", 1440, 90),
        Y1("1Y", 1440, 365),
        ALL("ALL", 21600, 0);   // sinceDays 0 means "since = 0" (all history Kraken has)

        fun sinceSec(): Long =
            if (this == ALL) 0L else System.currentTimeMillis() / 1000 - sinceDays * 86400
    }

    /** How often the wallpaper refetches. 5 minutes for every timeframe, as requested. */
    val interval: Long get() = 5 * 60 * 1000L

    fun timeframe(ctx: Context): Timeframe {
        val name = PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY_TIMEFRAME, Timeframe.H24.name)
        return try { Timeframe.valueOf(name ?: Timeframe.H24.name) } catch (e: Exception) { Timeframe.H24 }
    }

    fun setTimeframe(ctx: Context, tf: Timeframe) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(KEY_TIMEFRAME, tf.name).apply()
    }

    fun bgUri(ctx: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY_BG_URI, null)

    fun setBgUri(ctx: Context, uri: String?) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(KEY_BG_URI, uri).apply()
    }

    fun showBtc(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_SHOW_BTC, true)
    fun showEth(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_SHOW_ETH, true)
    fun showLink(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_SHOW_LINK, true)
    fun trendOnly(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(KEY_TREND_ONLY, false)

    /** 0..100, defaults to 75 (roughly where the rider always sat before this was adjustable). */
    fun carLocation(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx).getInt(KEY_CAR_LOCATION, 75)

    fun setCarLocation(ctx: Context, value: Int) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putInt(KEY_CAR_LOCATION, value).apply()
    }

    fun setBool(ctx: Context, key: String, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putBoolean(key, value).apply()
    }
}
