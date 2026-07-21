package ua.pp.edmphonkradio

import android.animation.ValueAnimator
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.analytics.AnalyticsListener

class RadioService : Service() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    lateinit var syncManager: RadioSyncManager
        private set
    private val binder = RadioBinder()

    inner class RadioBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    fun getAudioSessionId(): Int = exoPlayer.audioSessionId

    var onAudioSessionReady: ((Int) -> Unit)? = null
    var onTrackChanged: ((String) -> Unit)? = null

    private val prefs by lazy { getSharedPreferences("radio_prefs", Context.MODE_PRIVATE) }
    private var fadeAnimator: ValueAnimator? = null
    var currentTrackLabel: String = ""
        private set

    override fun onCreate() {
        super.onCreate()
        syncManager = RadioSyncManager(this)
        exoPlayer = ExoPlayer.Builder(this).build()
        
        mediaSession = MediaSessionCompat(this, "RadioService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { playRadio() }
                override fun onPause() { pauseRadio() }
            })
            isActive = true
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    playRadio() // Re-sync on end
                }
                updateNotification()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                updateNotification()
            }
        })

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                onAudioSessionReady?.invoke(audioSessionId)
            }
        })
    }

    fun playRadio() {
        if (prefs.getBoolean("wifi_only", false) && !isWifiConnected()) {
            Toast.makeText(this, "Увімкнено «лише Wi-Fi», а мережі Wi-Fi немає 📶", Toast.LENGTH_LONG).show()
            return
        }

        val sync = syncManager.getCurrentPosition()
        val url = syncManager.getBaseUrl() + sync.track.file
        val mediaItem = MediaItem.fromUri(Uri.parse(url))

        currentTrackLabel = trackDisplayName(sync.track.file)
        onTrackChanged?.invoke(currentTrackLabel)

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(sync.offsetMs)

        if (prefs.getBoolean("fade_in", true)) {
            fadeInVolume()
        } else {
            exoPlayer.volume = 1f
        }
        exoPlayer.play()

        startForeground(1, createNotification())
    }

    fun pauseRadio() {
        fadeAnimator?.cancel()
        exoPlayer.volume = 1f
        exoPlayer.pause()
        stopForeground(false)
        updateNotification()
    }

    private fun fadeInVolume() {
        fadeAnimator?.cancel()
        exoPlayer.volume = 0f
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            addUpdateListener { exoPlayer.volume = it.animatedValue as Float }
            start()
        }
    }

    private fun trackDisplayName(file: String): String {
        val nameOnly = file.substringBeforeLast(".")
        return "Трек $nameOnly"
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isPlaying(): Boolean = exoPlayer.isPlaying

    private fun updatePlaybackState() {
        val state = if (exoPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .build()
        )
    }

    private fun createNotification(): Notification {
        val channelId = "radio_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Radio Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (exoPlayer.isPlaying) {
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause",
                getServicePendingIntent(PlaybackStateCompat.ACTION_PAUSE))
        } else {
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Play",
                getServicePendingIntent(PlaybackStateCompat.ACTION_PLAY))
        }

        val subtitle = if (prefs.getBoolean("show_track_in_notif", true) && currentTrackLabel.isNotEmpty()) {
            currentTrackLabel
        } else {
            "24/7 Live Stream"
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("EDM PHONK RADIO")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0))
            .setOngoing(exoPlayer.isPlaying)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, createNotification())
    }

    private fun getServicePendingIntent(action: Long): PendingIntent {
        val intent = Intent(this, RadioService::class.java).apply {
            this.action = action.toString()
        }
        return PendingIntent.getService(this, action.toInt(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlaybackStateCompat.ACTION_PLAY.toString() -> playRadio()
            PlaybackStateCompat.ACTION_PAUSE.toString() -> pauseRadio()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        exoPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }
}
