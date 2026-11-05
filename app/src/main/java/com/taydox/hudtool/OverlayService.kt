package com.taydox.hudtool

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Region
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import androidx.media3.datasource.RawResourceDataSource
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import org.json.JSONArray
import org.json.JSONObject

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    // Layer data class
    data class OverlayLayer(
        val view: View,
        val params: WindowManager.LayoutParams,
        val imageView: ImageView,
        val playerView: PlayerView,
        val sandbox: FrameLayout,
        val dragHandle: LinearLayout,
        val resizeHandle: View,
        val resizeHandleLeft: View,
        val root: RelativeLayout,
        val gridView: PerspectiveGridView,
        val matrix: Matrix = Matrix(),
        var scaleFactor: Float = 1f,
        var isLocked: Boolean = false,
        var isGrayscale: Boolean = false,
        var isFlippedX: Boolean = false,
        var isFlippedY: Boolean = false,
        var opacityPercent: Int = 75,
        var imageInitialized: Boolean = false,
        var isVideo: Boolean = false,
        var player: ExoPlayer? = null,
        var uri: String = "",
        var gridMode: PerspectiveGridView.Mode = PerspectiveGridView.Mode.NONE,
        var isGridLocked: Boolean = false,
        
        // Video overlay controls
        val overlayScrubber: SeekBar? = null,
        val btnPrev: View? = null,
        val btnNext: View? = null,
        val llVideoOverlayControls: View? = null
    )

    private val layers = mutableListOf<OverlayLayer>()
    private var activeLayerIndex = 0

    private var isUpdating = false

    // Control panel
    private var controlPanel: View? = null
    private var controlPanelWrapper: FrameLayout? = null
    private lateinit var controlParams: WindowManager.LayoutParams
    
    private var sessionStartTime: Long = 0

    // Auto-hide
    private val autoHideHandler  = Handler(Looper.getMainLooper())
    private val AUTO_HIDE_DELAY  = 10_000L
    private val autoHideRunnable = Runnable { collapsePanel() }
    private var isPanelExpanded  = true

    private val scrubberHandler = Handler(Looper.getMainLooper())
    private val scrubberRunnable = object : Runnable {
        override fun run() {
            updateScrubber()
            scrubberHandler.postDelayed(this, 500)
        }
    }

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

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        showForegroundNotification()

        when (intent?.action) {
            "UPDATE_OPACITY" -> {
                val v = intent.getFloatExtra("OPACITY_VALUE", 1.0f)
                layers.getOrNull(activeLayerIndex)?.let {
                    it.opacityPercent = (v * 100).toInt()
                    if (it.isVideo) it.playerView.alpha = v else it.imageView.alpha = v
                }
                updateOpacityDisplay()
                return START_STICKY
            }
            "ADD_LAYER" -> {
                val uriStr = intent.getStringExtra("IMAGE_URI") ?: return START_STICKY
                val opacity = intent.getFloatExtra("START_OPACITY", 0.75f)
                if (controlPanel == null) {
                    val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
                    createControlPanel(themedContext)
                }
                addLayer(uriStr, opacity)
                return START_STICKY
            }
            "SAVE_WORKSPACE" -> {
                saveCurrentWorkspace(intent.getStringExtra("WORKSPACE_NAME") ?: "New Workspace")
                return START_STICKY
            }
            "LOAD_WORKSPACE" -> {
                val json = intent.getStringExtra("WORKSPACE_JSON")
                if (json != null) {
                    if (layers.isNotEmpty()) stopAllLayers()
                    val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
                    createControlPanel(themedContext)
                    loadWorkspace(json)
                }
                return START_STICKY
            }
            "SHOW_TUTORIAL" -> {
                showVideoTutorial()
                return START_STICKY
            }
        }

        // Fresh start
        val imageUriString = intent?.getStringExtra("IMAGE_URI")
        val startOpacity   = intent?.getFloatExtra("START_OPACITY", 0.75f) ?: 0.75f

        if (layers.isNotEmpty()) stopAllLayers()

        if (imageUriString != null) addLayer(imageUriString, startOpacity)
        
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        createControlPanel(themedContext)
        resetAutoHideTimer()

        return START_STICKY
    }

    // ── Layer management ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun addLayer(imageUriString: String, opacity: Float,
                         initialParams: WindowManager.LayoutParams? = null,
                         initialMatrixValues: FloatArray? = null) {
        
        if (layers.isEmpty()) {
            sessionStartTime = System.currentTimeMillis()
        }

        val pickedUri = Uri.parse(imageUriString)
        val pickedIsVideo = contentResolver.getType(pickedUri)?.startsWith("video") == true ||
                imageUriString.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") }

        if (pickedIsVideo && !ThemeUtils.isPro(this)) {
            ThemeUtils.showPremiumUpgradePrompt(this as? androidx.appcompat.app.AppCompatActivity ?: return)
            return
        }

        if (pickedIsVideo && layers.any { it.isVideo }) {
            android.widget.Toast.makeText(this, "Only one video overlay allowed for performance", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (layers.size >= 4) return // max 4 layers
        
        // Multi-layer restriction for Free users (post-trial)
        if (layers.size >= 1 && !ThemeUtils.isPro(this)) {
            ThemeUtils.showPremiumUpgradePrompt(this as? androidx.appcompat.app.AppCompatActivity ?: return)
            return
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.overlay_layout, null)

        val imageView        = view.findViewById<ImageView>(R.id.ivOverlayImage)
        val playerView       = view.findViewById<PlayerView>(R.id.pvOverlayVideo)
        val sandbox          = view.findViewById<FrameLayout>(R.id.imageSandbox)
        val dragHandle       = view.findViewById<LinearLayout>(R.id.dragHandle)
        val resizeHandle     = view.findViewById<View>(R.id.resizeHandle)
        val resizeHandleLeft = view.findViewById<View>(R.id.resizeHandleLeft)
        val root             = view.findViewById<RelativeLayout>(R.id.overlayRoot)
        val gridView         = view.findViewById<PerspectiveGridView>(R.id.pgvOverlayGrid)

        val overlayScrubber = view.findViewById<SeekBar>(R.id.sbOverlayScrubber)
        val btnPrev         = view.findViewById<View>(R.id.btnOverlayPrevFrame)
        val btnNext         = view.findViewById<View>(R.id.btnOverlayNextFrame)
        val llVideoOverlayControls = view.findViewById<View>(R.id.llVideoOverlayControls)

        var player: ExoPlayer? = null

        if (pickedIsVideo) {
            imageView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            
            llVideoOverlayControls?.visibility = View.VISIBLE
            
            player = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(pickedUri))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f // Disable sound for overlays
                prepare()
                playWhenReady = true
            }
            playerView.player = player
            playerView.alpha = opacity // Apply initial opacity to video
            
            // Set up scrubber listener
            overlayScrubber?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        player?.let { p ->
                            val duration = p.duration
                            if (duration > 0) {
                                val pos = (duration * progress) / 1000
                                p.seekTo(pos)
                            }
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    player?.pause() // Pause during scrubbing for better performance
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            // Set up frame step listeners
            btnPrev?.setOnClickListener {
                player?.let { p ->
                    p.pause()
                    p.seekTo(max(0, p.currentPosition - 33))
                    updateScrubber()
                }
            }
            btnNext?.setOnClickListener {
                player?.let { p ->
                    p.pause()
                    p.seekTo(min(p.duration, p.currentPosition + 33))
                    updateScrubber()
                }
            }

        } else {
            imageView.setImageURI(pickedUri)
        }

        val dm = resources.displayMetrics
        val offset = layers.size * 30

        val params = initialParams ?: WindowManager.LayoutParams(
            (300 * dm.density).toInt(),
            (350 * dm.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 + offset
            y = 100 + offset
        }

        val layer = OverlayLayer(
            view = view,
            params = params,
            imageView = imageView,
            playerView = playerView,
            sandbox = sandbox,
            dragHandle = dragHandle,
            resizeHandle = resizeHandle,
            resizeHandleLeft = resizeHandleLeft,
            root = root,
            opacityPercent = (opacity * 100).toInt(),
            isVideo = pickedIsVideo,
            player = player,
            overlayScrubber = overlayScrubber,
            btnPrev = btnPrev,
            btnNext = btnNext,
            llVideoOverlayControls = llVideoOverlayControls,
            uri = imageUriString,
            gridView = gridView
        )

        if (pickedIsVideo) playerView.alpha = opacity else imageView.alpha = opacity
        layers.add(layer)
        activeLayerIndex = layers.size - 1

        setupLayerTouchHandlers(layer)
        windowManager.addView(view, params)

        if (!pickedIsVideo) {
            sandbox.post { 
                if (initialMatrixValues != null) {
                    layer.matrix.setValues(initialMatrixValues)
                    layer.imageView.imageMatrix = layer.matrix
                    layer.imageInitialized = true
                } else {
                    initImageMatrix(layer) 
                }
            }
        }
        refreshLayerChips()
        updateControlsForActiveLayer()

        checkOverlayTutorial()
    }

    private fun checkOverlayTutorial() {
        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("showOverlayTutorial", true)) {
            showVideoTutorial()
        }
    }

    @OptIn(UnstableApi::class)
    private fun showVideoTutorial() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.tutorial_video_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        val btnNext = view.findViewById<Button>(R.id.btnTutorialNext)
        val tvTitle = view.findViewById<TextView>(R.id.tvTutorialTitle)
        val tvDesc  = view.findViewById<TextView>(R.id.tvTutorialDesc)
        val playerView = view.findViewById<PlayerView>(R.id.pvTutorialVideo)

        var step = 0
        val steps = listOf(
            Triple("Move & Resize", "Grab the top bar to move. Use the cyan corners to resize your reference.", ""),
            Triple("Lock for Drawing", "Tap the Lock icon to make the overlay pass-through. You can draw right under it!", ""),
            Triple("Precision Control", "Long-press Opacity for manual input, and use the save icon to store your setup.", "")
        )

        val player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
        playerView.player = player

        // Function to load video step
        fun loadStep(i: Int) {
            tvTitle.text = steps[i].first
            tvDesc.text  = steps[i].second
            
            val resId = when(i) {
                0 -> R.raw.tutorial_step_1
                1 -> R.raw.tutorial_step_2
                2 -> R.raw.tutorial_step_3
                else -> 0
            }
            
            if (resId != 0) {
                val uri = RawResourceDataSource.buildRawResourceUri(resId)
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.play()
            }
        }

        loadStep(0)
        
        btnNext.setOnClickListener {
            step++
            if (step < steps.size) {
                loadStep(step)
                if (step == steps.size - 1) btnNext.text = "LET'S DRAW!"
            } else {
                player.release()
                windowManager.removeView(view)
                getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit().putBoolean("showOverlayTutorial", false).apply()
            }
        }

        windowManager.addView(view, params)
    }

    private fun removeLayer(index: Int) {
        val layer = layers.getOrNull(index) ?: return
        layer.player?.release()
        try { windowManager.removeView(layer.view) } catch (e: Exception) { }
        layers.removeAt(index)
        activeLayerIndex = if (layers.isEmpty()) 0 else (index - 1).coerceAtLeast(0)
        refreshLayerChips()
        if (layers.isEmpty()) {
            stopSelf()
        } else {
            updateControlsForActiveLayer()
        }
    }

    private val activeLayer get() = layers.getOrNull(activeLayerIndex)

    // ── Image matrix ────────────────────────────────────────────────────────

    private fun initImageMatrix(layer: OverlayLayer) {
        val drawable = layer.imageView.drawable ?: return
        val sw = layer.sandbox.width.toFloat()
        val sh = layer.sandbox.height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return

        layer.scaleFactor = min(sw / dw, sh / dh)
        val tx = (sw - dw * layer.scaleFactor) / 2f
        val ty = (sh - dh * layer.scaleFactor) / 2f

        layer.matrix.reset()
        layer.matrix.postScale(layer.scaleFactor, layer.scaleFactor)
        layer.matrix.postTranslate(tx, ty)
        layer.imageView.imageMatrix = layer.matrix
        layer.imageInitialized = true
    }

    // ── Touch handlers ──────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLayerTouchHandlers(layer: OverlayLayer) {
        val dragListener = createWindowDragListener(layer)
        layer.dragHandle.setOnTouchListener { v, event ->
            dragListener.onTouch(v, event)
        }
        setupResizeHandle(layer, layer.resizeHandle, fromRight = true)
        setupResizeHandle(layer, layer.resizeHandleLeft, fromRight = false)
        setupImagePanZoom(layer)
    }

    private fun setupResizeHandle(layer: OverlayLayer, handle: View, fromRight: Boolean) {
        var iw = 0; var ih = 0; var ix = 0; var itx = 0f; var ity = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    iw = layer.params.width; ih = layer.params.height
                    ix = layer.params.x; itx = event.rawX; ity = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - itx).toInt()
                    val dy = (event.rawY - ity).toInt()
                    if (fromRight) {
                        layer.params.width = max(150, iw + dx)
                    } else {
                        val nw = max(150, iw - dx)
                        layer.params.x = ix + (iw - nw)
                        layer.params.width = nw
                    }
                    layer.params.height = max(150, ih + dy)
                    try { windowManager.updateViewLayout(layer.view, layer.params) } catch (e: Exception) { }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImagePanZoom(layer: OverlayLayer) {
        val scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor.coerceIn(0.8f, 1.25f)
                    layer.scaleFactor = (layer.scaleFactor * factor).coerceIn(0.05f, 15f)
                    layer.matrix.postScale(factor, factor, detector.focusX, detector.focusY)
                    layer.imageView.imageMatrix = layer.matrix
                    return true
                }
            })

        var lastX = 0f; var lastY = 0f; var pointerCount = 0

        layer.sandbox.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y; pointerCount = 1; true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pointerCount = event.pointerCount; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount == 1 && !scaleDetector.isInProgress) {
                        layer.matrix.postTranslate(event.x - lastX, event.y - lastY)
                        layer.imageView.imageMatrix = layer.matrix
                    }
                    lastX = event.x; lastY = event.y; true
                }
                MotionEvent.ACTION_POINTER_UP -> { pointerCount = event.pointerCount - 1; true }
                MotionEvent.ACTION_UP -> { pointerCount = 0; true }
                else -> false
            }
        }
    }

    private fun createWindowDragListener(layer: OverlayLayer): View.OnTouchListener {
        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = layer.params.x; iy = layer.params.y
                    itx = event.rawX; ity = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    layer.params.x = ix + (event.rawX - itx).toInt()
                    layer.params.y = iy + (event.rawY - ity).toInt()
                    try { windowManager.updateViewLayout(layer.view, layer.params) } catch (e: Exception) { }
                    true
                }
                else -> false
            }
        }
    }

    // ── Control panel ───────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createControlPanel(context: Context) {
        val inflater = LayoutInflater.from(context)
        controlPanel = inflater.inflate(R.layout.control_panel_layout, null)
        
        // Wrap the control panel in a FrameLayout to handle scaling and touch targets properly
        controlPanelWrapper = FrameLayout(context)
        controlPanelWrapper!!.addView(controlPanel)

        val screenWidth = resources.displayMetrics.widthPixels

        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth; y = 200
        }

        windowManager.addView(controlPanelWrapper, controlParams)
        controlPanelWrapper?.post {
            val pw = controlPanel?.width ?: 0
            controlParams.x = screenWidth - pw
            try { windowManager.updateViewLayout(controlPanelWrapper!!, controlParams) } catch (e: Exception) { }
            applyPanelScale()
        }

        setupControlPanelListeners()
        refreshLayerChips()
        updateControlsForActiveLayer()
        updateOpacityDisplay()
        scrubberHandler.post(scrubberRunnable)
    }

    private fun applyPanelScale() {
        val panel = controlPanel ?: return
        val wrapper = controlPanelWrapper ?: return
        
        wrapper.post {
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
            
            // Visual Scale
            panel.pivotX = 0f
            panel.pivotY = 0f
            panel.scaleX = scale
            panel.scaleY = scale
            
            // Physical Recalculation
            val dm = resources.displayMetrics
            val maxAllowedHeight = (dm.heightPixels * 0.9f).toInt()
            
            controlParams.width = scaledWidth
            controlParams.height = min(scaledHeight, maxAllowedHeight)
            
            // Anchor to edge
            val onLeft = controlParams.x < (screenWidth - scaledWidth) / 2
            if (onLeft) {
                controlParams.x = 0
            } else {
                controlParams.x = screenWidth - scaledWidth
            }

            try { windowManager.updateViewLayout(wrapper, controlParams) } catch (e: Exception) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlPanelListeners() {
        val btnToggle  = controlPanel?.findViewById<TextView>(R.id.btnTogglePanel)
        val btnAddLayer = controlPanel?.findViewById<ImageButton>(R.id.btnAddLayer)
        val btnSaveWorkspace = controlPanel?.findViewById<ImageButton>(R.id.btnSaveWorkspace)
        val btnLock    = controlPanel?.findViewById<ImageButton>(R.id.btnLock)
        val btnGray    = controlPanel?.findViewById<ImageButton>(R.id.btnGrayscale)
        val btnFlipH   = controlPanel?.findViewById<ImageButton>(R.id.btnFlipH)
        val btnFlipV   = controlPanel?.findViewById<ImageButton>(R.id.btnFlipV)
        val tvOpacity  = controlPanel?.findViewById<TextView>(R.id.tvOverlayOpacity)
        val btnClose   = controlPanel?.findViewById<ImageButton>(R.id.btnClose)
        val btnGridCycle = controlPanel?.findViewById<ImageButton>(R.id.btnGridCycle)
        val tvGridModeLabel = controlPanel?.findViewById<TextView>(R.id.tvGridModeLabel)

        val llPrecisionTray = controlPanel?.findViewById<LinearLayout>(R.id.llPrecisionTray)
        val flPrecisionSlider = controlPanel?.findViewById<View>(R.id.flPrecisionSlider)
        val sbPrecisionOpacity = controlPanel?.findViewById<SeekBar>(R.id.sbPrecisionOpacity)
        val etPrecisionOpacity = controlPanel?.findViewById<EditText>(R.id.etPrecisionOpacity)
        val etPrecisionX = controlPanel?.findViewById<EditText>(R.id.etPrecisionX)
        val etPrecisionY = controlPanel?.findViewById<EditText>(R.id.etPrecisionY)

        val llVideoControls = controlPanel?.findViewById<LinearLayout>(R.id.llVideoControls)
        val btnVideoPlayPause = controlPanel?.findViewById<ImageButton>(R.id.btnVideoPlayPause)
        val btnPanelPrevFrame = controlPanel?.findViewById<ImageButton>(R.id.btnPanelPrevFrame)
        val btnPanelNextFrame = controlPanel?.findViewById<ImageButton>(R.id.btnPanelNextFrame)
        val btnSpeed025 = controlPanel?.findViewById<TextView>(R.id.btnSpeed025)
        val btnSpeed05 = controlPanel?.findViewById<TextView>(R.id.btnSpeed05)
        val btnSpeed1 = controlPanel?.findViewById<TextView>(R.id.btnSpeed1)
        val btnSpeed2 = controlPanel?.findViewById<TextView>(R.id.btnSpeed2)

        tvOpacity?.setOnLongClickListener {
            resetAutoHideTimer()
            val isVisible = llPrecisionTray?.visibility == View.VISIBLE
            llPrecisionTray?.visibility = if (isVisible) View.GONE else View.VISIBLE
            flPrecisionSlider?.visibility = if (isVisible) View.GONE else View.VISIBLE
            
            // Toggle focusability so keyboard works
            if (!isVisible) {
                controlParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            } else {
                controlParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try { windowManager.updateViewLayout(controlPanelWrapper!!, controlParams) } catch (e: Exception) { }
            updatePrecisionInputs()
            applyPanelScale()
            true
        }

        btnSaveWorkspace?.setOnClickListener {
            resetAutoHideTimer()
            if (ThemeUtils.isPro(this)) {
                showSaveWorkspaceDialog()
            } else {
                ThemeUtils.showPremiumUpgradePrompt(this as? androidx.appcompat.app.AppCompatActivity ?: return@setOnClickListener)
            }
        }

        btnVideoPlayPause?.setOnClickListener {
            activeLayer?.player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
                btnVideoPlayPause.setImageResource(
                    if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }
        }

        btnPanelPrevFrame?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.player?.let { p ->
                p.pause()
                p.seekTo(max(0, p.currentPosition - 33))
                updateScrubber()
            }
        }

        btnPanelNextFrame?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.player?.let { p ->
                p.pause()
                p.seekTo(min(p.duration, p.currentPosition + 33))
                updateScrubber()
            }
        }

        val speedChips = listOf(btnSpeed025 to 0.25f, btnSpeed05 to 0.5f, btnSpeed1 to 1.0f, btnSpeed2 to 2.0f)
        speedChips.forEach { (chip, speed) ->
            chip?.setOnClickListener {
                activeLayer?.player?.setPlaybackSpeed(speed)
                speedChips.forEach { (c, _) ->
                    c?.setBackgroundResource(R.drawable.timer_chip_bg)
                    c?.setTextColor(0xFFA0AAB0.toInt())
                }
                chip.setBackgroundResource(R.drawable.timer_chip_selected_bg)
                chip.setTextColor(0xFF00C0BF.toInt())
            }
        }

        sbPrecisionOpacity?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isUpdating) {
                    activeLayer?.let {
                        it.opacityPercent = progress
                        val v = progress / 100f
                        if (it.isVideo) it.playerView.alpha = v else it.imageView.alpha = v
                        updateOpacityDisplay()
                        isUpdating = true
                        etPrecisionOpacity?.setText(progress.toString())
                        isUpdating = false
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        etPrecisionOpacity?.addTextChangedListener {
            if (!isUpdating) {
                val s = it.toString()
                if (s.isNotEmpty()) {
                    val p = s.toIntOrNull()?.coerceIn(10, 100) ?: 75
                    activeLayer?.let { layer ->
                        layer.opacityPercent = p
                        val v = p / 100f
                        if (layer.isVideo) layer.playerView.alpha = v else layer.imageView.alpha = v
                        updateOpacityDisplay()
                    }
                }
            }
        }

        etPrecisionX?.addTextChangedListener {
            if (!isUpdating) {
                val s = it.toString()
                if (s.isNotEmpty()) {
                    activeLayer?.let { layer ->
                        layer.params.x = s.toIntOrNull() ?: layer.params.x
                        try { windowManager.updateViewLayout(layer.view, layer.params) } catch (e: Exception) { }
                    }
                }
            }
        }

        etPrecisionY?.addTextChangedListener {
            if (!isUpdating) {
                val s = it.toString()
                if (s.isNotEmpty()) {
                    activeLayer?.let { layer ->
                        layer.params.y = s.toIntOrNull() ?: layer.params.y
                        try { windowManager.updateViewLayout(layer.view, layer.params) } catch (e: Exception) { }
                    }
                }
            }
        }

        // Hide add layer button on free builds if not pro/trial
        btnAddLayer?.visibility = if (ThemeUtils.isPro(this)) View.VISIBLE else View.GONE

        // Large invisible touch target for toggle/drag
        val flToggle = controlPanel?.findViewById<FrameLayout>(R.id.flToggleContainer)
        var isDragging = false
        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f
        var hapticTriggered = false

        flToggle?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    hapticTriggered = false
                    ix = controlParams.x; iy = controlParams.y
                    itx = event.rawX; ity = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - itx)
                    val dy = abs(event.rawY - ity)
                    if (dx > 10f || dy > 10f) {
                        if (!isDragging && !hapticTriggered) {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            hapticTriggered = true
                        }
                        isDragging = true
                        resetAutoHideTimer()
                        controlParams.x = ix + (event.rawX - itx).toInt()
                        controlParams.y = iy + (event.rawY - ity).toInt()
                        try { windowManager.updateViewLayout(controlPanelWrapper!!, controlParams) } catch (e: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - itx
                    val swipeThreshold = 50f
                    val screenWidth = resources.displayMetrics.widthPixels
                    val onLeft = controlParams.x < screenWidth / 2
                    
                    val isSwipeExpand = !isPanelExpanded && ((onLeft && deltaX > swipeThreshold) || (!onLeft && deltaX < -swipeThreshold))
                    
                    if (isSwipeExpand) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        expandPanel()
                    } else if (isDragging) {
                        snapToEdge()
                    } else {
                        // It was a tap
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        resetAutoHideTimer()
                        if (isPanelExpanded) collapsePanel() else expandPanel()
                    }
                    true
                }
                else -> false
            }
        }

        btnAddLayer?.setOnClickListener {
            resetAutoHideTimer()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_LAYER_PICKER", true)
            }
            startActivity(intent)
        }

        btnLock?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.let { toggleLock(it) }
        }

        btnGray?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.let { layer ->
                layer.isGrayscale = !layer.isGrayscale
                val m = ColorMatrix()
                if (layer.isGrayscale) m.setSaturation(0f) else m.setSaturation(1f)
                layer.imageView.colorFilter = ColorMatrixColorFilter(m)
                btnGray.alpha = if (layer.isGrayscale) 0.45f else 1.0f
            }
        }

        btnFlipH?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.let { layer ->
                layer.isFlippedX = !layer.isFlippedX
                layer.imageView.scaleX = if (layer.isFlippedX) -1f else 1f
                btnFlipH.alpha = if (layer.isFlippedX) 0.45f else 1.0f
            }
        }

        btnFlipV?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.let { layer ->
                layer.isFlippedY = !layer.isFlippedY
                layer.imageView.scaleY = if (layer.isFlippedY) -1f else 1f
                btnFlipV.alpha = if (layer.isFlippedY) 0.45f else 1.0f
            }
        }

        tvOpacity?.setOnClickListener {
            resetAutoHideTimer()
            activeLayer?.let { layer ->
                layer.opacityPercent -= 10
                if (layer.opacityPercent < 10) layer.opacityPercent = 100
                val v = layer.opacityPercent / 100f
                if (layer.isVideo) layer.playerView.alpha = v else layer.imageView.alpha = v
                updateOpacityDisplay()
            }
        }

        btnClose?.setOnClickListener {
            if (layers.size <= 1) {
                stopAllLayers()
                stopSelf()
            } else {
                removeLayer(activeLayerIndex)
            }
        }

        btnGridCycle?.setOnClickListener {
            resetAutoHideTimer()
            if (ThemeUtils.isPro(this)) {
                cycleLayerGridMode()
            } else {
                ThemeUtils.showPremiumUpgradePrompt(this as? androidx.appcompat.app.AppCompatActivity ?: return@setOnClickListener)
            }
        }

        btnGridCycle?.setOnLongClickListener {
            resetAutoHideTimer()
            if (ThemeUtils.isPro(this)) {
                activeLayer?.let { layer ->
                    layer.isGridLocked = !layer.isGridLocked
                    layer.gridView.isLocked = layer.isGridLocked
                    val msg = if (layer.isGridLocked) "Grid Locked - Image Move Active" else "Grid Unlocked - Point Edit Active"
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                    updateControlsForActiveLayer()
                }
            } else {
                ThemeUtils.showPremiumUpgradePrompt(this as? androidx.appcompat.app.AppCompatActivity ?: return@setOnLongClickListener true)
            }
            true
        }
    }

    private fun cycleLayerGridMode() {
        val layer = activeLayer ?: return
        val nextMode = when (layer.gridMode) {
            PerspectiveGridView.Mode.NONE -> PerspectiveGridView.Mode.GRID
            PerspectiveGridView.Mode.GRID -> PerspectiveGridView.Mode.ISOMETRIC
            PerspectiveGridView.Mode.ISOMETRIC -> PerspectiveGridView.Mode.ONE_POINT
            PerspectiveGridView.Mode.ONE_POINT -> PerspectiveGridView.Mode.TWO_POINT
            PerspectiveGridView.Mode.TWO_POINT -> PerspectiveGridView.Mode.THREE_POINT
            PerspectiveGridView.Mode.THREE_POINT -> PerspectiveGridView.Mode.CURVILINEAR
            PerspectiveGridView.Mode.CURVILINEAR -> PerspectiveGridView.Mode.NONE
        }

        layer.gridMode = nextMode
        layer.gridView.mode = nextMode
        layer.gridView.visibility = if (nextMode == PerspectiveGridView.Mode.NONE) View.GONE else View.VISIBLE
        
        controlPanel?.findViewById<TextView>(R.id.tvGridModeLabel)?.text = when (nextMode) {
            PerspectiveGridView.Mode.NONE -> "OFF"
            PerspectiveGridView.Mode.GRID -> "GRID"
            PerspectiveGridView.Mode.ISOMETRIC -> "ISO"
            PerspectiveGridView.Mode.ONE_POINT -> "1P"
            PerspectiveGridView.Mode.TWO_POINT -> "2P"
            PerspectiveGridView.Mode.THREE_POINT -> "3P"
            PerspectiveGridView.Mode.CURVILINEAR -> "CURV"
        }
    }

    // ── Layer chips ─────────────────────────────────────────────────────────

    private fun refreshLayerChips() {
        val llChips = controlPanel?.findViewById<LinearLayout>(R.id.llLayerChips) ?: return
        llChips.removeAllViews()

        layers.forEachIndexed { index, layer ->
            val chip = TextView(this).apply {
                text = "L${index + 1}"
                textSize = 11f
                setTextColor(if (index == activeLayerIndex) 0xFF00C0BF.toInt() else 0xFFA0AAB0.toInt())
                setBackgroundResource(
                    if (index == activeLayerIndex) R.drawable.layer_chip_selected_bg
                    else R.drawable.layer_chip_bg
                )
                setPadding(16, 8, 16, 8)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = 6 }
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeLayerIndex = index
                    refreshLayerChips()
                    updateControlsForActiveLayer()
                    updatePrecisionInputs()
                }
            }
            llChips.addView(chip)
        }
    }

    private fun updateControlsForActiveLayer() {
        val layer = activeLayer ?: return
        val btnLock  = controlPanel?.findViewById<ImageButton>(R.id.btnLock)
        val btnGray  = controlPanel?.findViewById<ImageButton>(R.id.btnGrayscale)
        val btnFlipH = controlPanel?.findViewById<ImageButton>(R.id.btnFlipH)
        val btnFlipV = controlPanel?.findViewById<ImageButton>(R.id.btnFlipV)
        val llVideoControls = controlPanel?.findViewById<LinearLayout>(R.id.llVideoControls)
        val btnVideoPlayPause = controlPanel?.findViewById<ImageButton>(R.id.btnVideoPlayPause)
        val tvGridLabel = controlPanel?.findViewById<TextView>(R.id.tvGridModeLabel)

        llVideoControls?.visibility = if (layer.isVideo) View.VISIBLE else View.GONE

        tvGridLabel?.text = when (layer.gridMode) {
            PerspectiveGridView.Mode.NONE -> "OFF"
            PerspectiveGridView.Mode.GRID -> "GRID"
            PerspectiveGridView.Mode.ISOMETRIC -> "ISO"
            PerspectiveGridView.Mode.ONE_POINT -> "1P"
            PerspectiveGridView.Mode.TWO_POINT -> "2P"
            PerspectiveGridView.Mode.THREE_POINT -> "3P"
            PerspectiveGridView.Mode.CURVILINEAR -> "CURV"
        }
        tvGridLabel?.setTextColor(if (layer.isGridLocked) 0xFFFFA500.toInt() else 0xFF00C0BF.toInt()) // Orange if locked

        if (layer.isVideo) {
            btnVideoPlayPause?.setImageResource(
                if (layer.player?.isPlaying == true) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
            // Update scrubber position
            updateScrubber()
        }

        btnLock?.setImageResource(
            if (layer.isLocked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
        )
        btnLock?.setBackgroundResource(
            if (layer.isLocked) R.drawable.panel_btn_green_dim_bg else R.drawable.panel_btn_green_bg
        )
        btnGray?.alpha  = if (layer.isGrayscale) 0.45f else 1.0f
        btnFlipH?.alpha = if (layer.isFlippedX)  0.45f else 1.0f
        btnFlipV?.alpha = if (layer.isFlippedY)  0.45f else 1.0f
        updateOpacityDisplay()
    }

    // ── Lock ────────────────────────────────────────────────────────────────

    private fun toggleLock(layer: OverlayLayer) {
        layer.isLocked = !layer.isLocked
        val btnLock = controlPanel?.findViewById<ImageButton>(R.id.btnLock)
        
        // Update grid lock state
        layer.gridView.isLocked = layer.isLocked

        if (layer.isLocked) {
            layer.dragHandle.visibility      = View.INVISIBLE
            layer.resizeHandle.visibility    = View.INVISIBLE
            layer.resizeHandleLeft.visibility = View.INVISIBLE
            
            if (layer.isVideo) {
                // Hide video controls while locked to ensure total pass-through
                layer.llVideoOverlayControls?.visibility = View.GONE
            }
            
            // Apply total pass-through flags for both images and videos
            layer.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            
            layer.root.setBackgroundColor(0x1A000000.toInt())
            btnLock?.setImageResource(R.drawable.ic_lock_closed)
            btnLock?.setBackgroundResource(R.drawable.panel_btn_green_dim_bg)
        } else {
            layer.dragHandle.visibility      = View.VISIBLE
            layer.resizeHandle.visibility    = View.VISIBLE
            layer.resizeHandleLeft.visibility = View.VISIBLE
            
            if (layer.isVideo) {
                layer.llVideoOverlayControls?.visibility = View.VISIBLE
            }

            layer.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layer.root.setBackgroundColor(0x66000000.toInt())
            btnLock?.setImageResource(R.drawable.ic_lock_open)
            btnLock?.setBackgroundResource(R.drawable.panel_btn_green_bg)
        }
        
        try { windowManager.updateViewLayout(layer.view, layer.params) } catch (e: Exception) { }
    }

    private fun setTouchableRegion(layer: OverlayLayer) {
        try {
            val view = layer.view
            val vri = view.parent ?: return
            val setMethod: Method = vri.javaClass.getDeclaredMethod("setTouchableRegion", Region::class.java)
            setMethod.isAccessible = true
            
            val region = Region()
            val rect = Rect()
            
            // Define areas that SHOULD remain touchable
            layer.btnPrev?.getGlobalVisibleRect(rect)
            region.op(rect, Region.Op.UNION)
            layer.btnNext?.getGlobalVisibleRect(rect)
            region.op(rect, Region.Op.UNION)
            
            // Note: because the VRI expects coordinates relative to the window, 
            // and getGlobalVisibleRect gives screen coords, we may need to adjust 
            // if the window isn't at 0,0. But usually for overlays it works.
            
            setMethod.invoke(vri, region)
        } catch (e: Exception) { }
    }

    private fun clearTouchableRegion(layer: OverlayLayer) {
        try {
            val vri = layer.view.parent ?: return
            val setMethod: Method = vri.javaClass.getDeclaredMethod("setTouchableRegion", Region::class.java)
            setMethod.isAccessible = true
            setMethod.invoke(vri, null as Region?)
        } catch (e: Exception) { }
    }

    // ── Panel show/hide ─────────────────────────────────────────────────────

    private fun expandPanel() {
        isPanelExpanded = true
        controlPanel?.findViewById<View>(R.id.nsvPanelButtons)?.visibility = View.VISIBLE
        val isTrayVisible = controlPanel?.findViewById<View>(R.id.llPrecisionTray)?.visibility == View.VISIBLE
        controlPanel?.findViewById<View>(R.id.flPrecisionSlider)?.visibility = if (isTrayVisible) View.VISIBLE else View.GONE
        controlPanel?.findViewById<TextView>(R.id.btnTogglePanel)?.text = "‹"
        controlPanel?.alpha = 1.0f
        resetAutoHideTimer()
        applyPanelScale()
    }

    private fun collapsePanel() {
        isPanelExpanded = false
        controlPanel?.findViewById<View>(R.id.nsvPanelButtons)?.visibility = View.GONE
        controlPanel?.findViewById<View>(R.id.flPrecisionSlider)?.visibility = View.GONE
        controlPanel?.findViewById<TextView>(R.id.btnTogglePanel)?.text = "›"
        controlPanel?.alpha = 0.45f
        autoHideHandler.removeCallbacks(autoHideRunnable)
        controlPanel?.post { snapToEdge() }
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val currentWidth = controlParams.width
        val centerX = controlParams.x + currentWidth / 2
        
        controlParams.x = if (centerX < screenWidth / 2) 0 else screenWidth - currentWidth
        try { windowManager.updateViewLayout(controlPanelWrapper!!, controlParams) } catch (e: Exception) { }
        
        applyPanelScale()
    }

    private fun resetAutoHideTimer() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY)
    }

    private fun updateOpacityDisplay() {
        if (isUpdating) return
        val pct = activeLayer?.opacityPercent ?: 75
        controlPanel?.findViewById<TextView>(R.id.tvOverlayOpacity)?.text = "$pct%"
        updatePrecisionInputs()
    }

    private fun updatePrecisionInputs() {
        if (isUpdating) return
        val layer = activeLayer ?: return
        val llPrecisionTray = controlPanel?.findViewById<LinearLayout>(R.id.llPrecisionTray)
        if (llPrecisionTray?.visibility != View.VISIBLE) return

        isUpdating = true
        val sbPrecisionOpacity = controlPanel?.findViewById<SeekBar>(R.id.sbPrecisionOpacity)
        val etPrecisionOpacity = controlPanel?.findViewById<EditText>(R.id.etPrecisionOpacity)
        val etPrecisionX = controlPanel?.findViewById<EditText>(R.id.etPrecisionX)
        val etPrecisionY = controlPanel?.findViewById<EditText>(R.id.etPrecisionY)

        sbPrecisionOpacity?.progress = layer.opacityPercent
        etPrecisionOpacity?.setText(layer.opacityPercent.toString())
        etPrecisionX?.setText(layer.params.x.toString())
        etPrecisionY?.setText(layer.params.y.toString())
        isUpdating = false
    }

    private fun updateScrubber() {
        val layer = activeLayer ?: return
        if (!layer.isVideo) return
        val player = layer.player ?: return
        
        val overlayScrubber = layer.overlayScrubber
        
        val duration = player.duration
        if (duration > 0) {
            val progress = (player.currentPosition * 1000 / duration).toInt()
            overlayScrubber?.progress = progress
        }
    }

    private fun showSaveWorkspaceDialog() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
        val builder = android.app.AlertDialog.Builder(themedContext)
        builder.setTitle("Save Workspace")
        
        val input = EditText(themedContext).apply {
            hint = "Workspace Name"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF606C75.toInt())
            setPadding(40, 30, 40, 30)
        }
        builder.setView(input)
        
        builder.setPositiveButton("Save") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                saveCurrentWorkspace(name)
            }
        }
        builder.setNegativeButton("Cancel", null)
        
        val dialog = builder.create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    // ── Workspace Management ────────────────────────────────────────────────

    private fun saveCurrentWorkspace(name: String) {
        val rootObj = JSONObject()
        rootObj.put("name", name)
        
        val layersArr = JSONArray()
        layers.forEach { layer ->
            val lObj = JSONObject()
            lObj.put("uri", layer.uri)
            lObj.put("x", layer.params.x)
            lObj.put("y", layer.params.y)
            lObj.put("width", layer.params.width)
            lObj.put("height", layer.params.height)
            lObj.put("opacity", layer.opacityPercent)
            lObj.put("isVideo", layer.isVideo)
            lObj.put("isLocked", layer.isLocked)
            lObj.put("isGrayscale", layer.isGrayscale)
            lObj.put("isFlippedX", layer.isFlippedX)
            lObj.put("isFlippedY", layer.isFlippedY)
            lObj.put("gridMode", layer.gridMode.name)
            lObj.put("isGridLocked", layer.isGridLocked)
            
            // Save vanishing points
            val vpObj = JSONObject()
            vpObj.put("vp1_x", layer.gridView.vp1.x)
            vpObj.put("vp1_y", layer.gridView.vp1.y)
            vpObj.put("vp2_x", layer.gridView.vp2.x)
            vpObj.put("vp2_y", layer.gridView.vp2.y)
            vpObj.put("vp3_x", layer.gridView.vp3.x)
            vpObj.put("vp3_y", layer.gridView.vp3.y)
            vpObj.put("vpV_x", layer.gridView.vpVertical.x)
            vpObj.put("vpV_y", layer.gridView.vpVertical.y)
            lObj.put("vanishingPoints", vpObj)
            
            if (!layer.isVideo) {
                val matrixValues = FloatArray(9)
                layer.matrix.getValues(matrixValues)
                val mArr = JSONArray()
                matrixValues.forEach { mArr.put(it.toDouble()) }
                lObj.put("matrix", mArr)
            }
            layersArr.put(lObj)
        }
        rootObj.put("layers", layersArr)

        val prefs = getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        val workspacesStr = prefs.getString("savedWorkspaces", "[]")
        val workspacesArr = JSONArray(workspacesStr)
        
        // Remove old workspace with same name if exists
        val newWorkspacesArr = JSONArray()
        for (i in 0 until workspacesArr.length()) {
            if (workspacesArr.getJSONObject(i).getString("name") != name) {
                newWorkspacesArr.put(workspacesArr.getJSONObject(i))
            }
        }
        newWorkspacesArr.put(rootObj)
        
        prefs.edit().putString("savedWorkspaces", newWorkspacesArr.toString()).apply()
        android.widget.Toast.makeText(this, "Workspace '$name' saved!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun loadWorkspace(jsonStr: String) {
        try {
            val rootObj = JSONObject(jsonStr)
            val layersArr = rootObj.getJSONArray("layers")
            
            for (i in 0 until layersArr.length()) {
                val lObj = layersArr.getJSONObject(i)
                val uri = lObj.getString("uri")
                val opacity = lObj.getInt("opacity") / 100f
                
                val params = WindowManager.LayoutParams(
                    lObj.getInt("width"),
                    lObj.getInt("height"),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = lObj.getInt("x")
                    y = lObj.getInt("y")
                }
                
                var matrixValues: FloatArray? = null
                if (lObj.has("matrix")) {
                    val mArr = lObj.getJSONArray("matrix")
                    matrixValues = FloatArray(9)
                    for (j in 0 until 9) {
                        matrixValues[j] = mArr.getDouble(j).toFloat()
                    }
                }
                
                addLayer(uri, opacity, params, matrixValues)
                
                // Re-apply flags and state
                val layer = layers.last()
                layer.isLocked = lObj.optBoolean("isLocked", false)
                layer.isGrayscale = lObj.optBoolean("isGrayscale", false)
                layer.isFlippedX = lObj.optBoolean("isFlippedX", false)
                layer.isFlippedY = lObj.optBoolean("isFlippedY", false)

                // Restore Grid Settings
                val savedGridMode = lObj.optString("gridMode", "NONE")
                layer.gridMode = PerspectiveGridView.Mode.valueOf(savedGridMode)
                layer.gridView.mode = layer.gridMode
                layer.gridView.visibility = if (layer.gridMode == PerspectiveGridView.Mode.NONE) View.GONE else View.VISIBLE
                
                layer.isGridLocked = lObj.optBoolean("isGridLocked", false)
                layer.gridView.isLocked = layer.isGridLocked

                if (lObj.has("vanishingPoints")) {
                    val vp = lObj.getJSONObject("vanishingPoints")
                    layer.gridView.vp1 = PointF(vp.getDouble("vp1_x").toFloat(), vp.getDouble("vp1_y").toFloat())
                    layer.gridView.vp2 = PointF(vp.getDouble("vp2_x").toFloat(), vp.getDouble("vp2_y").toFloat())
                    layer.gridView.vp3 = PointF(vp.getDouble("vp3_x").toFloat(), vp.getDouble("vp3_y").toFloat())
                    layer.gridView.vpVertical = PointF(vp.getDouble("vpV_x").toFloat(), vp.getDouble("vpV_y").toFloat())
                }
                
                // Re-sync UI state if locked
                if (layer.isLocked) {
                    layer.isLocked = false // Reset so toggleLock actually toggles it ON
                    toggleLock(layer)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error loading workspace", e)
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────────────────

    private fun stopAllLayers() {
        if (sessionStartTime > 0) {
            val durationMs = System.currentTimeMillis() - sessionStartTime
            val minutes = durationMs / (1000 * 60)
            if (minutes > 0) {
                FeedbackManager.addUsageMinutes(this, minutes)
            }
            sessionStartTime = 0
        }

        autoHideHandler.removeCallbacks(autoHideRunnable)
        scrubberHandler.removeCallbacks(scrubberRunnable)
        layers.forEach { layer ->
            layer.player?.release()
            try { windowManager.removeView(layer.view) } catch (e: Exception) { }
        }
        layers.clear()
        try {
            if (controlPanelWrapper != null) {
                windowManager.removeView(controlPanelWrapper)
                controlPanelWrapper = null
                controlPanel = null
            }
        } catch (e: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "HUD_SERVICE_CHANNEL",
                "HUD Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "HUD_SERVICE_CHANNEL")
            .setContentTitle("HUD is Active")
            .setContentText("Reference overlays are running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllLayers()
        try {
            getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {}
    }
}