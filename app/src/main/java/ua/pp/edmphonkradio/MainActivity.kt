package ua.pp.edmphonkradio

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var radioService: RadioService? = null
    private var isBound = false
    private lateinit var btnPlay: FrameLayout
    private lateinit var playIcon: ImageView
    private lateinit var visualizer: VisualizerView
    private lateinit var logo: ImageView
    private lateinit var statTracks: TextView
    private lateinit var statCycle: TextView
    private lateinit var subtitle: TextView
    private lateinit var currentTrackName: TextView
    private lateinit var liveDot: android.view.View
    private var pulseAnimator: ObjectAnimator? = null

    private val prefs by lazy { getSharedPreferences("radio_prefs", Context.MODE_PRIVATE) }
    private var visualizerEnabled = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RadioService.RadioBinder
            radioService = binder.getService()
            isBound = true
            updateButtonUI()
            populateStats()

            radioService?.onAudioSessionReady = { sessionId ->
                runOnUiThread {
                    if (visualizerEnabled) visualizer.attachToSession(sessionId)
                }
            }
            radioService?.onTrackChanged = { label ->
                runOnUiThread {
                    currentTrackName.text = label
                }
            }
            radioService?.let {
                if (it.isPlaying() && visualizerEnabled) {
                    visualizer.attachToSession(it.getAudioSessionId())
                }
                if (it.currentTrackLabel.isNotEmpty()) {
                    currentTrackName.text = it.currentTrackLabel
                }
                if (!it.isPlaying() && prefs.getBoolean("autoplay", false)) {
                    it.playRadio()
                    updateButtonUI()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        visualizerEnabled = prefs.getBoolean("visualizer_enabled", true)

        btnPlay = findViewById(R.id.btnPlay)
        playIcon = findViewById(R.id.playIcon)
        visualizer = findViewById(R.id.visualizer)
        logo = findViewById(R.id.logo)
        statTracks = findViewById(R.id.statTracks)
        statCycle = findViewById(R.id.statCycle)
        liveDot = findViewById(R.id.liveDot)
        currentTrackName = findViewById(R.id.currentTrackName)

        findViewById<ImageView>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "🎧 EDM PHONK RADIO — 24/7 Manifest of Time\nhttps://edmphonkradio.pp.ua/")
            }
            startActivity(Intent.createChooser(shareIntent, "Поділитись"))
        }

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            showSettingsSheet()
        }

        findViewById<ImageView>(R.id.btnTimer).setOnClickListener {
            Toast.makeText(this, "Таймер сну скоро з'явиться! ⏳", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageView>(R.id.btnHistory).setOnClickListener {
            Toast.makeText(this, "Історія маніфесту в розробці! 📜", Toast.LENGTH_SHORT).show()
        }

        setupButtonPressAnimation()
        startLivePulse()

        btnPlay.setOnClickListener {
            if (radioService?.isPlaying() == true) {
                radioService?.pauseRadio()
                visualizer.release()
            } else {
                radioService?.playRadio()
            }
            updateButtonUI()
        }

        val intent = Intent(this, RadioService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun showSettingsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setContentView(view)

        val optionVisualizer = view.findViewById<SwitchCompat>(R.id.optionVisualizer)
        val optionAutoplay = view.findViewById<SwitchCompat>(R.id.optionAutoplay)
        val optionFadeIn = view.findViewById<SwitchCompat>(R.id.optionFadeIn)
        val optionTrackInNotif = view.findViewById<SwitchCompat>(R.id.optionTrackInNotif)
        val optionWifiOnly = view.findViewById<SwitchCompat>(R.id.optionWifiOnly)
        val optionClearCache = view.findViewById<TextView>(R.id.optionClearCache)
        val optionAbout = view.findViewById<TextView>(R.id.optionAbout)

        optionVisualizer.isChecked = visualizerEnabled
        optionAutoplay.isChecked = prefs.getBoolean("autoplay", false)
        optionFadeIn.isChecked = prefs.getBoolean("fade_in", true)
        optionTrackInNotif.isChecked = prefs.getBoolean("show_track_in_notif", true)
        optionWifiOnly.isChecked = prefs.getBoolean("wifi_only", false)

        optionVisualizer.setOnCheckedChangeListener { _, isChecked ->
            visualizerEnabled = isChecked
            prefs.edit().putBoolean("visualizer_enabled", visualizerEnabled).apply()
            if (!visualizerEnabled) visualizer.release()
            else radioService?.let { if (it.isPlaying()) visualizer.attachToSession(it.getAudioSessionId()) }
        }

        optionAutoplay.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("autoplay", isChecked).apply() }
        optionFadeIn.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("fade_in", isChecked).apply() }
        optionTrackInNotif.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("show_track_in_notif", isChecked).apply() }
        optionWifiOnly.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("wifi_only", isChecked).apply() }

        optionClearCache.setOnClickListener {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "Кеш очищено ✅", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        optionAbout.setOnClickListener {
            Toast.makeText(this, "EDM PHONK RADIO v2.0\nManifest of Time 24/7", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    private fun setupButtonPressAnimation() {
        btnPlay.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun startLivePulse() {
        pulseAnimator = ObjectAnimator.ofFloat(liveDot, "alpha", 1f, 0.2f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun populateStats() {
        val sync = radioService?.syncManager ?: return
        statTracks.text = "${sync.trackCount} PCS"
        val totalSec = sync.totalDurationSeconds.roundToInt()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        statCycle.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun updateButtonUI() {
        if (radioService?.isPlaying() == true) {
            playIcon.setImageResource(R.drawable.ic_pause_bars)
            playIcon.translationX = 0f
        } else {
            playIcon.setImageResource(R.drawable.ic_play_triangle)
            playIcon.translationX = 4f
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonUI()
        radioService?.let { if (it.isPlaying() && visualizerEnabled) visualizer.attachToSession(it.getAudioSessionId()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
        visualizer.release()
        if (isBound) unbindService(connection)
    }
}
