package com.taydox.hudtool

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream

class WebSearchActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var prefs: SharedPreferences

    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_IMAGE_URI = "imageUri"
    private val KEY_IMAGE_PENDING = "pendingImageUri"
    private val KEY_HIDE_TUTORIAL = "hideWebTutorial"
    private val DEFAULT_URL = "https://za.pinterest.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_search)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        webView              = findViewById(R.id.webView)
        etUrl                = findViewById(R.id.etUrl)
        val btnBack          = findViewById<ImageButton>(R.id.btnWebBack)
        val btnForward       = findViewById<ImageButton>(R.id.btnWebForward)
        val btnClose         = findViewById<ImageButton>(R.id.btnWebClose)
        val btnCapture       = findViewById<TextView>(R.id.btnCaptureOverlay)
        val llTutorial       = findViewById<LinearLayout>(R.id.llTutorial)
        val cbDontShow       = findViewById<CheckBox>(R.id.cbDontShow)
        val btnDismiss       = findViewById<Button>(R.id.btnTutorialDismiss)

        // Show tutorial on first open
        if (!prefs.getBoolean(KEY_HIDE_TUTORIAL, false)) {
            llTutorial.visibility = View.VISIBLE
        }

        btnDismiss.setOnClickListener {
            if (cbDontShow.isChecked) {
                prefs.edit().putBoolean(KEY_HIDE_TUTORIAL, true).apply()
            }
            llTutorial.visibility = View.GONE
        }

        // WebView setup
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                etUrl.setText(request.url.toString())
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                etUrl.setText(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // could show a progress bar here
            }
        }

        // Long press to grab image
        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            if (result.type == WebView.HitTestResult.IMAGE_TYPE ||
                result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val imgUrl = result.extra
                if (!imgUrl.isNullOrEmpty()) {
                    showLoadImageDialog(imgUrl)
                }
            }
            false
        }

        val targetUrl = intent.getStringExtra("TARGET_URL") ?: DEFAULT_URL
        webView.loadUrl(targetUrl)
        etUrl.setText(targetUrl)

        // URL bar
        etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                navigateTo(etUrl.text.toString())
                true
            } else false
        }

        btnBack.setOnClickListener    { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnClose.setOnClickListener   { finish() }

        btnCapture.setOnClickListener { captureAndOverlay() }

        ThemeUtils.applyTheme(this, true)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.applyTheme(this, false)
    }

    private fun navigateTo(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
        webView.loadUrl(url)
        etUrl.setText(url)
    }

    private fun showLoadImageDialog(imgUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Reference Found")
            .setMessage("Load this image directly into HUD?")
            .setPositiveButton("LOAD IMAGE") { _, _ ->
                loadUrlAsOverlay(imgUrl)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun loadUrlAsOverlay(imgUrl: String) {
        Toast.makeText(this, "Loading image...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val url = java.net.URL(imgUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val bitmap = android.graphics.BitmapFactory.decodeStream(input)

                if (bitmap != null) {
                    val file = File(cacheDir, "web_img_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
                    }
                    val uri = Uri.fromFile(file)
                    prefs.edit()
                        .putString(KEY_IMAGE_URI, uri.toString())
                        .putBoolean(KEY_IMAGE_PENDING, true)
                        .apply()

                    runOnUiThread {
                        Toast.makeText(this, "Image loaded into HUD!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Couldn't decode image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun captureAndOverlay() {
        try {
            // Screenshot the webview
            val bmp = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            webView.draw(canvas)

            // Save to cache
            val file = File(cacheDir, "web_capture_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }

            val uri = Uri.fromFile(file)
            prefs.edit()
                .putString(KEY_IMAGE_URI, uri.toString())
                .putBoolean(KEY_IMAGE_PENDING, true)
                .apply()

            Toast.makeText(this, "Screen captured!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}