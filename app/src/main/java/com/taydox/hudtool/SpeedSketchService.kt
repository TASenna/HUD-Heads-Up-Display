package com.taydox.hudtool

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.*
import android.view.HapticFeedbackConstants
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max

class SpeedSketchService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    
    private var controlPanel: View? = null
    private var controlPanelWrapper: FrameLayout? = null
    private var controlParams: WindowManager.LayoutParams? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "panelScale") {
            applyPanelScale()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }
    
    private var imageList = listOf<String>()
    private var currentIndex = 0
    private var timerSeconds = 60
    private var isClassMode = false
    private var opacityPercent = 75
    
    private var countDownTimer: CountDownTimer? = null
    private var isPaused = false
    private var isLocked = false
    private var timeRemainingMs: Long = 0
    private var totalSecondsElapsed = 0
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        createNotificationChannel()
        showForegroundNotification()

        imageList = intent.getStringArrayListExtra("IMAGE_LIST") ?: emptyList()
        timerSeconds = intent.getIntExtra("TIMER_SECONDS", 60)
        val shuffle = intent.getBooleanExtra("SHUFFLE", true)
        isClassMode = intent.getBooleanExtra("IS_CLASS_MODE", false)
        val startOpacity = intent.getFloatExtra("OPACITY", 0.75f)
        opacityPercent = (startOpacity * 100).toInt()

        if (shuffle) {
            imageList = imageList.shuffled()
        }

        if (overlayView == null) {
            createOverlay()
            createControlPanel()
        }
        
        startNextPose()
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val inflater = LayoutInflater.from(themedContext)
        overlayView = inflater.inflate(R.layout.speed_sketch_overlay, null)

        val dm = resources.displayMetrics
        overlayParams = WindowManager.LayoutParams(
            (240 * dm.density).toInt(),
            (280 * dm.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val btnPlayPause = overlayView!!.findViewById<ImageButton>(R.id.btnPlayPause)
        val btnSkip      = overlayView!!.findViewById<ImageButton>(R.id.btnSkip)
        val btnClose     = overlayView!!.findViewById<ImageButton>(R.id.btnClose)
        val ivImage      = overlayView!!.findViewById<ImageView>(R.id.ivPoseImage)
        val dragHandle   = overlayView!!.findViewById<View>(R.id.dragHandle)
        val resizeHandle = overlayView!!.findViewById<View>(R.id.resizeHandle)
        
        ivImage.alpha = opacityPercent / 100f

        btnPlayPause.setOnClickListener { togglePause() }
        btnSkip.setOnClickListener { startNextPose() }
        btnClose.setOnClickListener { stopSelf() }

        // Drag logic
        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = overlayParams!!.x; iy = overlayParams!!.y
                    itx = event.rawX; ity = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams!!.x = ix + (event.rawX - itx).toInt()
                    overlayParams!!.y = iy + (event.rawY - ity).toInt()
                    windowManager.updateViewLayout(overlayView, overlayParams); true
                }
                else -> false
            }
        }

        // Resize logic
        var iw = 0; var ih = 0
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    iw = overlayParams!!.width; ih = overlayParams!!.height
                    itx = event.rawX; ity = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    overlayParams!!.width = max(200, iw + (event.rawX - itx).toInt())
                    overlayParams!!.height = max(240, ih + (event.rawY - ity).toInt())
                    windowManager.updateViewLayout(overlayView, overlayParams); true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, overlayParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createControlPanel() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val inflater = LayoutInflater.from(themedContext)
        controlPanel = inflater.inflate(R.layout.speed_sketch_control_panel, null)
        
        controlPanelWrapper = FrameLayout(this)
        controlPanelWrapper!!.addView(controlPanel)

        val dm = resources.displayMetrics
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - (150 * dm.density).toInt()
            y = 300
        }

        val btnToggle = controlPanel!!.findViewById<TextView>(R.id.btnTogglePanel)
        val btnLock   = controlPanel!!.findViewById<ImageButton>(R.id.btnLock)
        val tvOpacity = controlPanel!!.findViewById<TextView>(R.id.tvOverlayOpacity)
        val llButtons = controlPanel!!.findViewById<View>(R.id.llPanelButtons)

        tvOpacity.text = "$opacityPercent%"

        btnLock.setOnClickListener {
            isLocked = !isLocked
            btnLock.setImageResource(if (isLocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open)
            btnLock.setBackgroundResource(if (isLocked) R.drawable.panel_btn_green_dim_bg else R.drawable.panel_btn_green_bg)
            
            overlayView!!.findViewById<View>(R.id.dragHandle).visibility = if (isLocked) View.GONE else View.VISIBLE
            overlayView!!.findViewById<View>(R.id.resizeHandle).visibility = if (isLocked) View.GONE else View.VISIBLE
            overlayView!!.findViewById<View>(R.id.llBottomControls).visibility = if (isLocked) View.GONE else View.VISIBLE
            
            if (isLocked) {
                overlayParams!!.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                overlayParams!!.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.updateViewLayout(overlayView, overlayParams)
        }

        tvOpacity.setOnClickListener {
            opacityPercent -= 10
            if (opacityPercent < 10) opacityPercent = 100
            tvOpacity.text = "$opacityPercent%"
            overlayView!!.findViewById<View>(R.id.ivPoseImage).alpha = opacityPercent / 100f
        }

        val flToggle = controlPanel!!.findViewById<FrameLayout>(R.id.flToggleContainer)
        var isDraggingPanel = false
        var px = 0; var py = 0
        var hapticTriggered = false

        flToggle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingPanel = false
                    hapticTriggered = false
                    px = controlParams!!.x; py = controlParams!!.y
                    itx = event.rawX; ity = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - itx)
                    val dy = Math.abs(event.rawY - ity)
                    if (dx > 10 || dy > 10) {
                        if (!isDraggingPanel && !hapticTriggered) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            hapticTriggered = true
                        }
                        isDraggingPanel = true
                        controlParams!!.x = px + (event.rawX - itx).toInt()
                        controlParams!!.y = py + (event.rawY - ity).toInt()
                        windowManager.updateViewLayout(controlPanelWrapper, controlParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - itx
                    val swipeThreshold = 50f
                    val screenWidth = resources.displayMetrics.widthPixels
                    val onLeft = controlParams!!.x < screenWidth / 2
                    val isExp = llButtons.visibility == View.VISIBLE
                    
                    val isSwipeExpand = !isExp && ((onLeft && deltaX > swipeThreshold) || (!onLeft && deltaX < -swipeThreshold))

                    if (isSwipeExpand) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        llButtons.visibility = View.VISIBLE
                        btnToggle.text = "‹"
                        applyPanelScale()
                    } else if (!isDraggingPanel) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        llButtons.visibility = if (isExp) View.GONE else View.VISIBLE
                        btnToggle.text = if (isExp) "›" else "‹"
                        applyPanelScale()
                    } else {
                        // Snap to edge when dragging ends
                        val sw = resources.displayMetrics.widthPixels
                        val currentWidth = controlParams!!.width
                        val centerX = controlParams!!.x + currentWidth / 2
                        controlParams!!.x = if (centerX < sw / 2) 0 else sw - currentWidth
                        windowManager.updateViewLayout(controlPanelWrapper, controlParams)
                        applyPanelScale()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(controlPanelWrapper, controlParams)
        applyPanelScale()
    }

    private fun applyPanelScale() {
        val panel = controlPanel ?: return
        val wrapper = controlPanelWrapper ?: return
        panel.post {
            // Measure natural size based on current visibility (expanded vs collapsed)
            val oldScaleX = panel.scaleX
            val oldScaleY = panel.scaleY
            panel.scaleX = 1.0f
            panel.scaleY = 1.0f
            
            panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val naturalWidth = panel.measuredWidth
            val naturalHeight = panel.measuredHeight
            
            panel.scaleX = oldScaleX
            panel.scaleY = oldScaleY

            val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
            val progress = prefs.getInt("panelScale", 50)
            val scale = 0.5f + (progress / 100f)
            
            val screenWidth = resources.displayMetrics.widthPixels
            val scaledWidth = (naturalWidth * scale).toInt()
            val scaledHeight = (naturalHeight * scale).toInt()
            
            // Fix clipping by using 0,0 pivot and resizing window
            panel.pivotX = 0f
            panel.pivotY = 0f
            panel.scaleX = scale
            panel.scaleY = scale
            
            controlParams!!.width = scaledWidth
            controlParams!!.height = scaledHeight
            
            val onLeft = controlParams!!.x < (screenWidth - scaledWidth) / 2
            if (onLeft) {
                controlParams!!.x = 0
            } else {
                controlParams!!.x = screenWidth - scaledWidth
            }
            try { windowManager.updateViewLayout(wrapper, controlParams) } catch (e: Exception) {}
        }
    }

    private var itx = 0f; private var ity = 0f // Panel drag temp

    private fun startNextPose() {
        if (currentIndex >= imageList.size) {
            finishSession()
            return
        }

        val ivImage = overlayView?.findViewById<ImageView>(R.id.ivPoseImage)
        val tvCounter = overlayView?.findViewById<TextView>(R.id.tvPoseCounter)
        
        ivImage?.setImageURI(Uri.parse(imageList[currentIndex]))
        tvCounter?.text = "${currentIndex + 1} / ${imageList.size}"

        val duration = if (isClassMode) {
            getClassModeDuration(currentIndex)
        } else {
            timerSeconds
        }

        currentIndex++
        startTimer(duration * 1000L)
    }

    private fun getClassModeDuration(index: Int): Int {
        return when {
            index < 5 -> 30   // First 5: 30s
            index < 10 -> 60  // Next 5: 1m
            index < 15 -> 120 // Next 5: 2m
            else -> 300       // Rest: 5m
        }
    }

    private fun startTimer(durationMs: Long) {
        countDownTimer?.cancel()
        timeRemainingMs = durationMs
        
        val bt = overlayView?.findViewById<BorderTimerView>(R.id.borderTimer)
        val tv = overlayView?.findViewById<TextView>(R.id.tvTimerDisplay)
        
        countDownTimer = object : CountDownTimer(durationMs, 50) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                totalSecondsElapsed++
                val progress = millisUntilFinished.toFloat() / durationMs
                bt?.setProgress(progress)
                tv?.text = (millisUntilFinished / 1000).toInt().toString()
            }

            override fun onFinish() {
                bt?.setProgress(0f)
                startNextPose()
            }
        }.start()
    }

    private fun togglePause() {
        val btn = overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
        if (isPaused) {
            startTimer(timeRemainingMs)
            btn?.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            countDownTimer?.cancel()
            btn?.setImageResource(android.R.drawable.ic_media_play)
        }
        isPaused = !isPaused
    }

    private fun finishSession() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("gesture_show_summary", true)
            .putInt("gesture_poses", currentIndex)
            .putInt("gesture_seconds", totalSecondsElapsed / 20) // Correct for 50ms interval
            .apply()

        val intent = Intent(this, SpeedSketchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SPEED_SKETCH_CHANNEL",
                "Speed Sketch Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val notificationIntent = Intent(this, SpeedSketchActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "SPEED_SKETCH_CHANNEL")
            .setContentTitle("Speed Sketch Active")
            .setContentText("Your drawing drill is in progress.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        controlPanelWrapper?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        try {
            getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {}
    }
}
