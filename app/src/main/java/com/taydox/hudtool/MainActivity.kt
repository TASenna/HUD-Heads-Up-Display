package com.taydox.hudtool

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import org.json.JSONArray
import com.taydox.hudtool.BuildConfig
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.util.TypedValue
import com.google.android.material.appbar.AppBarLayout
import coil.load
import coil.decode.VideoFrameDecoder
import coil.ImageLoader
import coil.request.videoFrameMillis

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null
    private lateinit var prefs: SharedPreferences
    private var isOverlayServiceRunning = false

    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_IMAGE_URI = "imageUri"
    private val KEY_OPACITY = "opacity"

    private lateinit var flImageContainer: FrameLayout
    private lateinit var llPlaceholder: LinearLayout
    private lateinit var ivSelectedImage: ImageView
    private lateinit var btnClearImage: ImageButton
    private lateinit var btnLaunch: Button
    private lateinit var btnStop: Button
    private lateinit var sbOpacity: SeekBar
    private lateinit var tvOpacityValue: TextView
    private lateinit var floatingSquaresView: FloatingSquaresView
    private lateinit var shimmerLaunch: ShimmerView
    private lateinit var shimmerStop: ShimmerView
    private lateinit var containerLaunch: FrameLayout
    private lateinit var containerStop: FrameLayout
    private lateinit var tvHudTitle: TextView
    private lateinit var glimmerProBadge: GlimmerBadgeView
    private lateinit var llSavedWorkspaces: LinearLayout

    private var currentAccentColor = Color.parseColor("#00C0BF")

    private val hudHandler = Handler(Looper.getMainLooper())
    private var isExpanded = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "savedWorkspaces") {
            loadSavedWorkspaces()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                handleImageSelected(uri)
            }
        }
    }

    private val pickLayerImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val isVideo = contentResolver.getType(uri)?.startsWith("video") == true
                if (isVideo && !ThemeUtils.isPro(this)) {
                    ThemeUtils.showPremiumUpgradePrompt(this)
                } else {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = "ADD_LAYER"
                        putExtra("IMAGE_URI", uri.toString())
                        putExtra("START_OPACITY", sbOpacity.progress / 100f)
                    })
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_burger)?.apply {
            setTint(Color.parseColor("#00C0BF"))
        }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        floatingSquaresView = findViewById(R.id.floatingSquaresView)
        flImageContainer    = findViewById(R.id.flImageContainer)
        llPlaceholder       = findViewById(R.id.llPlaceholder)
        ivSelectedImage     = findViewById(R.id.ivSelectedImage)
        btnClearImage       = findViewById(R.id.btnClearImage)
        btnLaunch           = findViewById(R.id.btnLaunch)
        btnStop             = findViewById(R.id.btnStop)
        sbOpacity           = findViewById(R.id.sbOpacity)
        tvOpacityValue      = findViewById(R.id.tvOpacityValue)
        shimmerLaunch       = findViewById(R.id.shimmerLaunch)
        shimmerStop         = findViewById(R.id.shimmerStop)
        containerLaunch     = findViewById(R.id.containerLaunch)
        containerStop       = findViewById(R.id.containerStop)
        tvHudTitle          = findViewById(R.id.tvHudTitle)
        glimmerProBadge     = findViewById(R.id.glimmerProBadge)
        llSavedWorkspaces   = findViewById(R.id.llSavedWorkspaces)

        shimmerLaunch.baseColor = Color.parseColor("#2D6A5F")
        shimmerStop.baseColor   = Color.parseColor("#7A3B3B")

        tvHudTitle.setOnClickListener { if (!isExpanded) expandTitle() }
        btnClearImage.setOnClickListener { clearImage() }

        loadPreferences()
        setupListeners()
        updateButtonStates()
        loadSavedWorkspaces()

        hudHandler.postDelayed({ expandTitle() }, 800)

        // Consolidated Pro Trial Initialization
        if (!BuildConfig.IS_PREMIUM) {
            val startTime = prefs.getLong(ThemeUtils.KEY_TRIAL_START, 0L)
            if (startTime == 0L) {
                prefs.edit().putLong(ThemeUtils.KEY_TRIAL_START, System.currentTimeMillis()).apply()
            }
        }

        // Show Pro badge for everyone, but with different intent for Free users
        updateProBadge()

        ThemeUtils.applyTheme(this, true)
        
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // Handle Edge-to-Edge System Bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startSplashAnimation()

        FeedbackManager.recordLaunch(this)
    }

    private fun checkTrialDialogs() {
        if (BuildConfig.IS_PREMIUM) return

        val daysLeft = ThemeUtils.getTrialDaysLeft(this)
        val awardedShown = prefs.getBoolean(ThemeUtils.KEY_TRIAL_AWARDED_SHOWN, false)
        val endedShown = prefs.getBoolean(ThemeUtils.KEY_TRIAL_ENDED_SHOWN, false)

        if (daysLeft > 0 && !awardedShown) {
            ThemeUtils.showTrialAwardedDialog(this)
        } else if (daysLeft <= 0 && awardedShown && !endedShown) {
            ThemeUtils.showTrialEndedDialog(this)
            updateProBadge() // Refresh UI to show "GET PRO" instead of trial
        }
    }

    private fun startSplashAnimation() {
        startTrialTimer()
        val overlay = findViewById<View>(R.id.flSplashOverlay)
        val logo = findViewById<TextView>(R.id.tvSplashLogo)

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTablet) 72f else 44f)
        
        logo.alpha = 0f
        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        logo.text = "HUD"
        
        logo.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1000)
            .withEndAction {
                // Typewriter expansion
                val target = "HEADS-UP-DISPLAY"
                var charIndex = 4
                val runnable = object : Runnable {
                    override fun run() {
                        if (charIndex <= target.length) {
                            logo.text = target.substring(0, charIndex)
                            charIndex++
                            hudHandler.postDelayed(this, 45)
                        } else {
                            // Contract and Dissolve
                            hudHandler.postDelayed({
                                logo.animate()
                                    .scaleX(0.85f)
                                    .scaleY(0.85f)
                                    .alpha(0.6f)
                                    .setDuration(700)
                                    .withEndAction {
                                        overlay.animate()
                                            .alpha(0f)
                                            .setDuration(900)
                                            .withEndAction {
                                                overlay.visibility = View.GONE
                                                FeedbackManager.checkAndShowFeedback(this@MainActivity)
                                                checkTrialDialogs()
                                                checkFirstRun()
                                            }.start()
                                    }.start()
                            }, 1800)
                        }
                    }
                }
                hudHandler.postDelayed(runnable, 400)
            }.start()
    }

    private fun checkFirstRun() {
        val isFirstRun = prefs.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            showIntroTutorial()
        }
    }

    private fun updateProBadge() {
        ThemeUtils.updateProBadge(this)
    }

    private fun startTrialTimer() {
        if (BuildConfig.IS_PREMIUM) return
        hudHandler.postDelayed(object : Runnable {
            override fun run() {
                updateProBadge()
                hudHandler.postDelayed(this, 30_000) // Refresh every 30 seconds
            }
        }, 30_000)
    }

    private fun showPremiumUpgradePrompt() {
        ThemeUtils.showPremiumUpgradePrompt(this)
    }

    private fun showIntroTutorial() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.tutorial_intro_layout, null)
        val dialog = AlertDialog.Builder(themedContext)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnNext = dialogView.findViewById<Button>(R.id.btnTutorialNext)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTutorialTitle)
        val tvDesc = dialogView.findViewById<TextView>(R.id.tvTutorialDesc)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivTutorialIcon)

        var step = 0
        val steps = listOf(
            Triple(if (BuildConfig.IS_PREMIUM) "Welcome to HUD Pro" else "Welcome to HUD", "Your ultimate companion for artistic reference. Let's get you started.", R.drawable.ic_launcher_foreground),
            Triple("Global Search", "Find inspiration directly within the app using our integrated web engine.", android.R.drawable.ic_menu_search),
            Triple("Speed Sketch", "Level up your anatomy and speed with professional timed drawing drills.", R.drawable.ic_gesture),
            Triple("Ready to Create?", "Launch an overlay to see the magic happen. We'll show you how that works next!", R.drawable.ic_add_layer)
        )

        btnNext.setOnClickListener {
            step++
            if (step < steps.size) {
                val (title, desc, icon) = steps[step]
                tvTitle.text = title
                tvDesc.text = desc
                ivIcon.setImageResource(icon)
                if (step == steps.size - 1) btnNext.text = "FINISH"
            } else {
                prefs.edit().putBoolean("isFirstRun", false).apply()
                dialog.dismiss()
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("OPEN_LAYER_PICKER", false)) {
            openLayerImagePicker()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.applyTheme(this, false)
        loadSavedWorkspaces()

        // Check for layer picker request
        if (intent.getBooleanExtra("OPEN_LAYER_PICKER", false)) {
            intent.removeExtra("OPEN_LAYER_PICKER")
            openLayerImagePicker()
        }

        // Existing pending image check
        val pending = prefs.getBoolean("pendingImageUri", false)
        if (pending) {
            prefs.edit().putBoolean("pendingImageUri", false).apply()
            val uriStr = prefs.getString(KEY_IMAGE_URI, null)
            if (uriStr != null) {
                selectedImageUri = Uri.parse(uriStr)
                updateImagePreview()
                updateButtonStates()
                addToRecentlyUsed(uriStr)
            }
        }
    }

    // ── HUD Typewriter ──────────────────────────────────────────────────────

    private fun expandTitle() {
        if (isExpanded) return
        isExpanded = true
        hudHandler.removeCallbacksAndMessages(null)
        val target = "HEADS-UP-DISPLAY"
        var index = 1
        val runnable = object : Runnable {
            override fun run() {
                if (index <= target.length) {
                    tvHudTitle.text = target.substring(0, index)
                    index++
                    hudHandler.postDelayed(this, 55)
                } else {
                    hudHandler.postDelayed({ collapseTitle() }, 3500)
                }
            }
        }
        hudHandler.post(runnable)
    }

    private fun collapseTitle() {
        var currentText = "HEADS-UP-DISPLAY"
        val runnable = object : Runnable {
            override fun run() {
                if (currentText.isNotEmpty()) {
                    currentText = currentText.dropLast(1)
                    tvHudTitle.text = currentText
                    hudHandler.postDelayed(this, 35)
                } else {
                    tvHudTitle.text = "HUD"
                    isExpanded = false
                }
            }
        }
        hudHandler.post(runnable)
    }

    // ── Touch / Ripple / Haptic ─────────────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            floatingSquaresView.triggerRipple(ev.x, ev.y)
            triggerHaptic()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(20)
            }
        } catch (e: Exception) { }
    }

    // ── Button States ───────────────────────────────────────────────────────

    private fun updateButtonStates() {
        when {
            selectedImageUri == null -> {
                containerLaunch.alpha = 0.35f
                btnLaunch.isEnabled = false
                shimmerLaunch.stopShimmer()
                containerStop.alpha = 0.35f
                btnStop.isEnabled = false
                shimmerStop.stopShimmer()
            }
            isOverlayServiceRunning -> {
                containerLaunch.alpha = 0.35f
                btnLaunch.isEnabled = false
                shimmerLaunch.stopShimmer()
                containerStop.alpha = 1f
                btnStop.isEnabled = true
                shimmerStop.startShimmer()
            }
            else -> {
                containerLaunch.alpha = 1f
                btnLaunch.isEnabled = true
                shimmerLaunch.startShimmer()
                containerStop.alpha = 0.35f
                btnStop.isEnabled = false
                shimmerStop.stopShimmer()
            }
        }
    }

    // ── Menu ────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.icon?.mutate()?.let {
                it.setTint(currentAccentColor)
                it.alpha = 255
            }
        }
        // Show Speed Sketch if Pro or Trial is active
        menu.findItem(R.id.action_gesture)?.isVisible = ThemeUtils.isPro(this)
        
        // Ensure tutorial is always visible
        menu.findItem(R.id.action_tutorial)?.isVisible = true

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(Intent(this, DiscoveryHubActivity::class.java)); true
            }
            R.id.action_library -> {
                startActivity(Intent(this, LibraryActivity::class.java)); true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java)); true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }

            R.id.action_tutorial -> {
                showIntroTutorial()
                // Also reset the video tutorial flag so it shows up next time they overlay
                prefs.edit().putBoolean("showOverlayTutorial", true).apply()
                if (isOverlayServiceRunning) {
                    startService(Intent(this, OverlayService::class.java).apply {
                        action = "SHOW_TUTORIAL"
                    })
                }
                true
            }
            R.id.action_gesture -> {
                startActivity(Intent(this, SpeedSketchActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Listeners ───────────────────────────────────────────────────────────

    private fun setupListeners() {
        flImageContainer.setOnClickListener { openImagePicker() }

        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacityValue.text = "$progress%"
                if (fromUser && isOverlayServiceRunning) {
                    startService(Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = "UPDATE_OPACITY"
                        putExtra("OPACITY_VALUE", progress / 100f)
                    })
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_OPACITY, seekBar?.progress ?: 74).apply()
            }
        })

        btnLaunch.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start(); true
                }
                MotionEvent.ACTION_UP -> {
                    val inside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                    if (inside && v.isEnabled) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(50).start()
                        }.start()
                        checkPermissionAndStart()
                    } else {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start(); true
                }
                else -> false
            }
        }

        btnStop.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start(); true
                }
                MotionEvent.ACTION_UP -> {
                    val inside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                    if (inside && v.isEnabled) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(50).start()
                        }.start()
                        stopOverlayService()
                    } else {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start(); true
                }
                else -> false
            }
        }
    }

    // ── Image ───────────────────────────────────────────────────────────────

    private fun openImagePicker() {
        pickImageLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val proActive = ThemeUtils.isPro(this@MainActivity)
            type = if (proActive) "*/*" else "image/*"
            if (proActive) {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }

    private fun handleImageSelected(uri: Uri) {
        val isVideo = contentResolver.getType(uri)?.startsWith("video") == true
        if (isVideo && !ThemeUtils.isPro(this)) {
            ThemeUtils.showPremiumUpgradePrompt(this)
            return
        }
        selectedImageUri = uri
        prefs.edit().putString(KEY_IMAGE_URI, uri.toString()).apply()
        addToRecentlyUsed(uri.toString())
        updateImagePreview()
        updateButtonStates()
    }

    private fun clearImage() {
        selectedImageUri = null
        prefs.edit().remove(KEY_IMAGE_URI).apply()
        ivSelectedImage.setImageURI(null)
        ivSelectedImage.visibility = View.GONE
        btnClearImage.visibility = View.GONE
        llPlaceholder.visibility = View.VISIBLE
        if (isOverlayServiceRunning) stopOverlayService()
        updateButtonStates()
    }

    private fun updateImagePreview() {
        if (selectedImageUri == null) {
            ivSelectedImage.visibility = View.GONE
            llPlaceholder.visibility = View.VISIBLE
            btnClearImage.visibility = View.GONE
            return
        }

        val isVideo = contentResolver.getType(selectedImageUri!!)?.startsWith("video") == true
        
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()

        ivSelectedImage.load(selectedImageUri, imageLoader) {
            crossfade(true)
            if (isVideo) {
                videoFrameMillis(1000)
            }
        }

        if (isVideo) {
            // Apply a subtle cyan tint so user knows it's a video
            ivSelectedImage.setColorFilter(Color.parseColor("#4000C0BF"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        } else {
            ivSelectedImage.clearColorFilter()
        }

        ivSelectedImage.visibility = View.VISIBLE
        llPlaceholder.visibility = View.GONE
        btnClearImage.visibility = View.VISIBLE
    }

    private fun loadPreferences() {
        val savedOpacity = prefs.getInt(KEY_OPACITY, 74)
        sbOpacity.progress = savedOpacity
        tvOpacityValue.text = "$savedOpacity%"
        val savedImageStr = prefs.getString(KEY_IMAGE_URI, null)
        if (savedImageStr != null) {
            selectedImageUri = Uri.parse(savedImageStr)
            updateImagePreview()
        }
    }

    private fun addToRecentlyUsed(uriStr: String) {
        val key = "recentImages"
        val arr = JSONArray(prefs.getString(key, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getString(i) != uriStr) newArr.put(arr.getString(i))
        }
        val final = JSONArray()
        final.put(uriStr)
        for (i in 0 until newArr.length()) final.put(newArr.getString(i))
        val trimmed = JSONArray()
        for (i in 0 until minOf(10, final.length())) trimmed.put(final.getString(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    // ── Overlay ─────────────────────────────────────────────────────────────

    private fun checkPermissionAndStart() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }
        if (Settings.canDrawOverlays(this)) startOverlayService()
        else startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun startOverlayService() {
        startService(Intent(this, OverlayService::class.java).apply {
            putExtra("IMAGE_URI", selectedImageUri?.toString())
            putExtra("START_OPACITY", sbOpacity.progress / 100f)
        })
        isOverlayServiceRunning = true
        updateButtonStates()
    }

    private fun openLayerImagePicker() {
        pickLayerImageLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val proActive = ThemeUtils.isPro(this@MainActivity)
            type = if (proActive) "*/*" else "image/*"
            if (proActive) {
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        })
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
        isOverlayServiceRunning = false
        updateButtonStates()
    }

    private fun loadSavedWorkspaces() {
        llSavedWorkspaces.removeAllViews()
        val jsonStr = prefs.getString("savedWorkspaces", "[]")
        val arr = JSONArray(jsonStr)

        if (arr.length() == 0) {
            val empty = TextView(this).apply {
                text = "No saved workspaces yet."
                setTextColor(Color.parseColor("#606C75"))
                textSize = 14f
                setPadding(16, 20, 16, 20)
            }
            llSavedWorkspaces.addView(empty)
            return
        }

        for (i in 0 until arr.length()) {
            val workspaceObj = arr.getJSONObject(i)
            val name = workspaceObj.getString("name")

            val chip = TextView(this).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.layer_chip_bg)
                setPadding(32, 16, 32, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 16 }
                layoutParams = lp
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    try {
                        startService(Intent(this@MainActivity, OverlayService::class.java).apply {
                            action = "LOAD_WORKSPACE"
                            putExtra("WORKSPACE_JSON", workspaceObj.toString())
                        })
                        isOverlayServiceRunning = true
                        updateButtonStates()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error loading workspace: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete Workspace")
                        .setMessage("Are you sure you want to delete '$name'?")
                        .setPositiveButton("Delete") { _, _ ->
                            deleteWorkspace(name)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            llSavedWorkspaces.addView(chip)
        }
    }

    private fun deleteWorkspace(name: String) {
        val jsonStr = prefs.getString("savedWorkspaces", "[]")
        val arr = JSONArray(jsonStr)
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("name") != name) {
                newArr.put(arr.getJSONObject(i))
            }
        }
        prefs.edit().putString("savedWorkspaces", newArr.toString()).apply()
        loadSavedWorkspaces()
    }

    override fun onDestroy() {
        super.onDestroy()
        hudHandler.removeCallbacksAndMessages(null)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}