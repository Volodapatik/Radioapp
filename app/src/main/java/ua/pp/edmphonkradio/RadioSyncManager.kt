package ua.pp.edmphonkradio

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

data class Track(val file: String, val duration: Double)

class RadioSyncManager(context: Context) {
    private val playlist: List<Track>
    private val totalDuration: Double

    val trackCount: Int get() = playlist.size
    val totalDurationSeconds: Double get() = totalDuration

    init {
        val inputStream = context.assets.open("manifest.json")
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<Track>>() {}.type
        playlist = Gson().fromJson(reader, type)
        totalDuration = playlist.sumOf { it.duration }
    }

    data class SyncPosition(val track: Track, val offsetMs: Long)

    fun getCurrentPosition(): SyncPosition {
        val nowSec = System.currentTimeMillis() / 1000.0
        val posInCycle = nowSec % totalDuration
        
        var acc = 0.0
        for (track in playlist) {
            if (posInCycle < acc + track.duration) {
                val offsetSec = posInCycle - acc
                return SyncPosition(track, (offsetSec * 1000).toLong())
            }
            acc += track.duration
        }
        return SyncPosition(playlist[0], 0L)
    }
    
    fun getBaseUrl(): String = "https://pub-0576ee0b3c8e43b8b22d474d166b6b60.r2.dev/"
}
