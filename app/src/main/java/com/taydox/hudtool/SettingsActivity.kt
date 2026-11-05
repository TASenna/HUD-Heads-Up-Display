package com.taydox.hudtool

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_OPACITY        = "opacity"
    private val KEY_AUTO_HIDE      = "autoHide"
    private val KEY_KEEP_SCREEN_ON = "keepScreenOn"
    private val KEY_HAPTIC         = "hapticFeedback"
    private val KEY_SOUND_EFFECTS  = "soundEffects"
    private val KEY_THEME_MODE     = "themeMode" // 0: Auto, 1: Light, 2: Dark
    private val KEY_PANEL_SCALE    = "panelScale" // Default 1.0f (stored as int 0-100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val switchAutoHide      = findViewById<SwitchCompat>(R.id.switchAutoHide)
        val switchKeepScreenOn  = findViewById<SwitchCompat>(R.id.switchKeepScreenOn)
        val switchHaptic        = findViewById<SwitchCompat>(R.id.switchHaptic)
        val switchSoundEffects  = findViewById<SwitchCompat>(R.id.switchSoundEffects)
        val rgThemeMode         = findViewById<RadioGroup>(R.id.rgThemeMode)
        val sbDefaultOpacity    = findViewById<SeekBar>(R.id.sbDefaultOpacity)
        val tvDefaultOpacity    = findViewById<TextView>(R.id.tvDefaultOpacityValue)
        val sbPanelScale        = findViewById<SeekBar>(R.id.sbPanelScale)
        val tvPanelScaleValue   = findViewById<TextView>(R.id.tvPanelScaleValue)
        val llDummyPanel        = findViewById<android.view.View>(R.id.llDummyPanel)
        val btnReset            = findViewById<MaterialButton>(R.id.btnResetSettings)

        // Load saved values
        switchAutoHide.isChecked     = prefs.getBoolean(KEY_AUTO_HIDE, true)
        switchKeepScreenOn.isChecked = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        switchHaptic.isChecked       = prefs.getBoolean(KEY_HAPTIC, true)
        switchSoundEffects.isChecked = prefs.getBoolean(KEY_SOUND_EFFECTS, true)
        
        val themeMode = prefs.getInt(KEY_THEME_MODE, 0)
        when (themeMode) {
            0 -> rgThemeMode.check(R.id.rbThemeAuto)
            1 -> rgThemeMode.check(R.id.rbThemeLight)
            2 -> rgThemeMode.check(R.id.rbThemeDark)
        }

        val savedOpacity = prefs.getInt(KEY_OPACITY, 75)
        sbDefaultOpacity.progress = savedOpacity
        tvDefaultOpacity.text = "$savedOpacity%"

        val savedScale = prefs.getInt(KEY_PANEL_SCALE, 50) // 50 represents 1.0x
        sbPanelScale.progress = savedScale
        updateScaleUI(savedScale, tvPanelScaleValue, llDummyPanel)

        // Listeners
        switchAutoHide.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_AUTO_HIDE, checked).apply()
        }
        switchKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, checked).apply()
        }
        switchHaptic.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_HAPTIC, checked).apply()
        }
        switchSoundEffects.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SOUND_EFFECTS, checked).apply()
        }
        
        rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeAuto  -> 0
                R.id.rbThemeLight -> 1
                R.id.rbThemeDark  -> 2
                else -> 0
            }
            prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        }

        sbDefaultOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDefaultOpacity.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_OPACITY, seekBar?.progress ?: 75).apply()
            }
        })

        sbPanelScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateScaleUI(progress, tvPanelScaleValue, llDummyPanel)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt(KEY_PANEL_SCALE, seekBar?.progress ?: 50).apply()
                // Overlay services use preference listener now
            }
        })

        btnReset.setOnClickListener {
            prefs.edit()
                .putBoolean(KEY_AUTO_HIDE, true)
                .putBoolean(KEY_KEEP_SCREEN_ON, true)
                .putBoolean(KEY_HAPTIC, true)
                .putBoolean(KEY_SOUND_EFFECTS, true)
                .putInt(KEY_THEME_MODE, 0)
                .putInt(KEY_OPACITY, 75)
                .putInt(KEY_PANEL_SCALE, 50)
                .apply()
            switchAutoHide.isChecked     = true
            switchKeepScreenOn.isChecked = true
            switchHaptic.isChecked       = true
            switchSoundEffects.isChecked = true
            rgThemeMode.check(R.id.rbThemeAuto)
            sbDefaultOpacity.progress    = 75
            tvDefaultOpacity.text        = "75%"
            sbPanelScale.progress        = 50
            updateScaleUI(50, tvPanelScaleValue, llDummyPanel)
            Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnSettingsFeedback).setOnClickListener {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_HUDReferenceOverlayTool)
            AlertDialog.Builder(themedContext)
                .setTitle("Feedback & Support")
                .setItems(arrayOf("Report a Bug", "Give Feedback")) { _, which ->
                    if (which == 0) {
                        // Report a Bug -> Email
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("thetaydox@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "HUD App Bug Report")
                            val version = if (BuildConfig.IS_PREMIUM) "Pro" else "Free"
                            putExtra(Intent.EXTRA_TEXT, "Hi Taydo,\n\nI'm using HUD $version and I found a bug:\n\n")
                        }
                        try { startActivity(Intent.createChooser(intent, "Report Bug")) } catch (e: Exception) {}
                    } else {
                        // Give Feedback -> Website
                        val itchUrl = "https://taydox.itch.io/hud-reference-overlay-tool"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(itchUrl))
                        try { startActivity(intent) } catch (e: Exception) {}
                    }
                }
                .show()
        }

        ThemeUtils.applyTheme(this, true)
        ThemeUtils.updateProBadge(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun updateScaleUI(progress: Int, tv: TextView, panel: android.view.View) {
        val scale = 0.5f + (progress / 100f) // 0.5x to 1.5x
        tv.text = String.format(java.util.Locale.US, "%.1fx", scale)
        panel.scaleX = scale
        panel.scaleY = scale
    }

    override fun onResume() {
        super.onResume()
        ThemeUtils.applyTheme(this, false)
    }
}