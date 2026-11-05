package com.taydox.hudtool

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

class SpeedSketchActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var llSetupScreen: View
    private lateinit var btnSelectAlbum: LinearLayout
    private lateinit var tvSelectedAlbum: TextView
    private lateinit var rgSessionMode: RadioGroup
    private lateinit var chip30s: TextView
    private lateinit var chip1m: TextView
    private lateinit var chip2m: TextView
    private lateinit var chip5m: TextView
    private lateinit var switchShuffle: SwitchCompat
    private lateinit var btnStartSession: MaterialButton
    
    private lateinit var llSummaryScreen: LinearLayout
    private lateinit var tvSummaryPoses: TextView
    private lateinit var tvSummaryTime: TextView
    private lateinit var btnSessionAgain: MaterialButton
    private lateinit var btnSummaryBack: MaterialButton

    private var selectedAlbumName = ""
    private var imageList = mutableListOf<String>()
    private var timerSeconds = 60
    private var selectedChip: TextView? = null
    
    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_ALBUMS = "savedAlbums"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_sketch)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { 
            if (llSummaryScreen.visibility == View.VISIBLE || findViewById<View>(R.id.flSessionScreen).visibility == View.VISIBLE) {
                showScreen("setup")
            } else {
                finish()
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        bindViews()
        setupChips()
        setupListeners()

        findViewById<FrameLayout>(R.id.flSessionScreen).visibility = View.GONE

        if (!ThemeUtils.isPro(this)) {
            ThemeUtils.showPremiumUpgradePrompt(this)
            finish()
            return
        }

        checkForSummary()

        ThemeUtils.applyTheme(this, true)
        ThemeUtils.updateProBadge(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (llSummaryScreen.visibility == View.VISIBLE || findViewById<View>(R.id.flSessionScreen).visibility == View.VISIBLE) {
                    showScreen("setup")
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.applyTheme(this, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkForSummary()
    }

    private fun checkForSummary() {
        if (prefs.getBoolean("gesture_show_summary", false)) {
            prefs.edit().putBoolean("gesture_show_summary", false).apply()
            val poses = prefs.getInt("gesture_poses", 0)
            val seconds = prefs.getInt("gesture_seconds", 0)
            showSummary(poses, seconds)
        }
    }

    private fun bindViews() {
        llSetupScreen   = findViewById(R.id.llSetupScreen)
        btnSelectAlbum  = findViewById(R.id.btnSelectAlbum)
        tvSelectedAlbum = findViewById(R.id.tvSelectedAlbum)
        rgSessionMode   = findViewById(R.id.rgSessionMode)
        chip30s         = findViewById(R.id.chip30s)
        chip1m          = findViewById(R.id.chip1m)
        chip2m          = findViewById(R.id.chip2m)
        chip5m          = findViewById(R.id.chip5m)
        switchShuffle   = findViewById(R.id.switchShuffle)
        btnStartSession = findViewById(R.id.btnStartSession)
        
        llSummaryScreen = findViewById(R.id.llSummaryScreen)
        tvSummaryPoses  = findViewById(R.id.tvSummaryPoses)
        tvSummaryTime   = findViewById(R.id.tvSummaryTime)
        btnSessionAgain = findViewById(R.id.btnSessionAgain)
        btnSummaryBack  = findViewById(R.id.btnSummaryBack)
    }

    private fun setupChips() {
        selectChip(chip1m, 60)
    }

    private fun selectChip(chip: TextView, seconds: Int) {
        selectedChip?.setBackgroundResource(R.drawable.timer_chip_bg)
        selectedChip?.setTextColor(Color.parseColor("#A0AAB0"))

        chip.setBackgroundResource(R.drawable.timer_chip_selected_bg)
        chip.setTextColor(Color.parseColor("#00C0BF"))

        selectedChip = chip
        timerSeconds = seconds
    }

    private fun setupListeners() {
        chip30s.setOnClickListener { selectChip(chip30s, 30) }
        chip1m.setOnClickListener { selectChip(chip1m, 60) }
        chip2m.setOnClickListener { selectChip(chip2m, 120) }
        chip5m.setOnClickListener { selectChip(chip5m, 300) }

        btnSelectAlbum.setOnClickListener { showAlbumPicker() }

        btnStartSession.setOnClickListener {
            if (selectedAlbumName.isEmpty()) {
                Toast.makeText(this, "Please select an album first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (imageList.isEmpty()) {
                Toast.makeText(this, "Selected album has no images", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            launchSpeedSketchOverlay()
        }

        btnSessionAgain.setOnClickListener {
            showScreen("setup")
        }

        btnSummaryBack.setOnClickListener {
            finish()
        }
        
        findViewById<View>(R.id.btnStopSessionManual).setOnClickListener {
            stopService(Intent(this, SpeedSketchService::class.java))
            showScreen("setup")
        }
    }

    private fun launchSpeedSketchOverlay() {
        val isClassMode = rgSessionMode.checkedRadioButtonId == R.id.rbModeClass
        val savedOpacity = prefs.getInt("opacity", 75) / 100f

        startService(Intent(this, SpeedSketchService::class.java).apply {
            putStringArrayListExtra("IMAGE_LIST", ArrayList(imageList))
            putExtra("TIMER_SECONDS", timerSeconds)
            putExtra("SHUFFLE", switchShuffle.isChecked)
            putExtra("IS_CLASS_MODE", isClassMode)
            putExtra("OPACITY", savedOpacity)
        })

        showScreen("session")
    }

    private fun showAlbumPicker() {
        val albums = getSavedAlbums()
        if (albums.isEmpty()) {
            Toast.makeText(this, "No albums yet — create one in Library first", Toast.LENGTH_SHORT).show()
            return
        }

        val names = albums.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Practice Set")
            .setItems(names) { _, index ->
                selectedAlbumName = albums[index].first
                imageList = albums[index].second.toMutableList()
                tvSelectedAlbum.text = "$selectedAlbumName (${imageList.size} images)"
                tvSelectedAlbum.setTextColor(Color.WHITE)
            }
            .show()
    }

    private fun getSavedAlbums(): List<Pair<String, List<String>>> {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val images = obj.optJSONArray("images") ?: JSONArray()
            Pair(name, (0 until images.length()).map { images.getString(it) })
        }
    }

    private fun showScreen(screen: String) {
        llSetupScreen.visibility = if (screen == "setup") View.VISIBLE else View.GONE
        findViewById<View>(R.id.flSessionScreen).visibility = if (screen == "session") View.VISIBLE else View.GONE
        llSummaryScreen.visibility = if (screen == "summary") View.VISIBLE else View.GONE
    }

    private fun showSummary(poses: Int, seconds: Int) {
        showScreen("summary")
        tvSummaryPoses.text = poses.toString()
        val minutes = seconds / 60
        tvSummaryTime.text = if (minutes < 1) "<1m" else "${minutes}m"
    }

    override fun onBackPressed() {
        // Handled by callback in onCreate
    }
}
