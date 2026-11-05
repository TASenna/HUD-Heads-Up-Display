package com.taydox.hudtool

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.app.Activity
import android.view.ContextThemeWrapper
import android.widget.Toast

object ThemeUtils {
    const val PREFS_NAME = "OverlayPrefs"
    const val KEY_THEME_MODE = "themeMode"
    const val KEY_TRIAL_START = "trial_start_timestamp"
    const val KEY_TRIAL_AWARDED_SHOWN = "trial_awarded_shown"
    const val KEY_TRIAL_ENDED_SHOWN = "trial_ended_shown"

    fun isPro(context: Context): Boolean {
        if (BuildConfig.IS_PREMIUM) return true
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startTime = prefs.getLong(KEY_TRIAL_START, 0L)
        if (startTime == 0L) return false
        
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - startTime) < threeDaysMs
    }

    fun getTrialDaysLeft(context: Context): Int {
        if (BuildConfig.IS_PREMIUM) return -1
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startTime = prefs.getLong(KEY_TRIAL_START, 0L)
        if (startTime == 0L) return 0
        
        val elapsed = System.currentTimeMillis() - startTime
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        val remaining = threeDaysMs - elapsed
        
        return if (remaining <= 0) 0 else (remaining / (24 * 60 * 60 * 1000L)).toInt() + 1
    }

    private var sessionTrialOverAcknowledged = false

    fun updateProBadge(activity: AppCompatActivity) {
        val badge = activity.findViewById<GlimmerBadgeView>(R.id.glimmerProBadge) ?: return
        
        if (BuildConfig.IS_PREMIUM) {
            badge.text = "PRO"
            badge.visibility = View.VISIBLE
            badge.setOnClickListener(null)
            return
        }

        val startTime = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_TRIAL_START, 0L)
        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val expired = startTime != 0L && (now - startTime) >= threeDaysMs

        if (expired) {
            if (!sessionTrialOverAcknowledged) {
                badge.text = "TRIAL OVER"
                badge.badgeColor = Color.RED
            } else {
                badge.text = "GET PRO"
                badge.badgeColor = 0xFFFFD700.toInt() // Gold
            }
        } else {
            val daysLeft = getTrialDaysLeft(activity)
            if (daysLeft > 0) {
                badge.text = "PRO TRIAL ($daysLeft Days)"
                badge.badgeColor = 0xFF00C0BF.toInt() // Cyan
            } else {
                badge.text = "GET PRO"
                badge.badgeColor = 0xFFFFD700.toInt() // Gold
            }
        }

        badge.visibility = View.VISIBLE
        badge.setOnClickListener {
            if (expired) sessionTrialOverAcknowledged = true
            showPremiumUpgradePrompt(activity)
            updateProBadge(activity) // Refresh after click
        }
    }

    fun showPremiumUpgradePrompt(activity: Activity) {
        val themedContext = ContextThemeWrapper(activity, R.style.Theme_HUDReferenceOverlayTool)
        AlertDialog.Builder(themedContext)
            .setTitle("HUD Pro")
            .setMessage("Upgrade to HUD Pro to unlock:\n\n" +
                    "• Overlay up to 4 images at once\n" +
                    "• Professional Video Overlays\n" +
                    "• Advanced Perspective Grids\n" +
                    "• Unlimited Workspace Profiles\n" +
                    "• Timed Speed Sketch Sessions\n" +
                    "• ad-free & premium experience")
            .setPositiveButton("GET HUD PRO") { _, _ ->
                val itchUrl = "https://taydox.itch.io/hud-reference-overlay-tool"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(itchUrl))
                try { activity.startActivity(intent) } catch (e: Exception) {
                    Toast.makeText(activity, "Could not open browser. Please visit itch.io", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("MAYBE LATER", null)
            .show()
    }

    fun showTrialAwardedDialog(activity: Activity) {
        val themedContext = ContextThemeWrapper(activity, R.style.Theme_HUDReferenceOverlayTool)
        AlertDialog.Builder(themedContext)
            .setTitle("Welcome to HUD!")
            .setMessage("Thank you for downloading the app! \n\n" +
                    "As a special welcome, you have been awarded a 3-Day Pro Trial.\n\n" +
                    "You can now use all Pro features: multi-image overlays, video support, Speed Sketch drills, and more!")
            .setPositiveButton("LET'S GO", null)
            .show()
        
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRIAL_AWARDED_SHOWN, true).apply()
    }

    fun showTrialEndedDialog(activity: Activity) {
        val themedContext = ContextThemeWrapper(activity, R.style.Theme_HUDReferenceOverlayTool)
        AlertDialog.Builder(themedContext)
            .setTitle("Pro Trial Ended")
            .setMessage("Your 3-day Pro trial has expired. We hope those features helped your workflow!\n\n" +
                    "Would you like to upgrade to HUD Pro to keep using multi-layers, video, and perspective tools?")
            .setPositiveButton("YES, GET PRO") { _, _ ->
                val itchUrl = "https://taydox.itch.io/hud-reference-overlay-tool"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(itchUrl))
                try { activity.startActivity(intent) } catch (e: Exception) {}
            }
            .setNegativeButton("NO THANKS", null)
            .show()

        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRIAL_ENDED_SHOWN, true).apply()
    }

    fun applyTheme(activity: AppCompatActivity, isInitial: Boolean) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeMode = prefs.getInt(KEY_THEME_MODE, 0)
        
        val isDark = when (themeMode) {
            1 -> false
            2 -> true
            else -> (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        val bgColor = if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F5F5F5")
        val appBarColor = if (isDark) Color.parseColor("#DD101010") else Color.parseColor("#E0E0E0")
        val gridColor = if (isDark) Color.parseColor("#4A4A4A") else Color.parseColor("#CCCCCC")
        val accentColor = if (isDark) 0x00C0BF else 0x008080
        val textColor = if (isDark) Color.parseColor("#A0AAB0") else Color.parseColor("#444444")
        val mainContentTextColor = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#333333")
        val titleColor = if (isDark) Color.parseColor("#00C0BF") else Color.parseColor("#008080")
        val secondaryTextColor = if (isDark) Color.parseColor("#808080") else Color.parseColor("#666666")

        val root = activity.findViewById<View>(R.id.mainRoot)
        val appBar = activity.findViewById<View>(R.id.appBarLayout)
        val fsv = activity.findViewById<FloatingSquaresView>(R.id.floatingSquaresView)

        if (isInitial) {
            root?.setBackgroundColor(bgColor)
            appBar?.setBackgroundColor(appBarColor)
            fsv?.setGridColor(gridColor)
            fsv?.setAccentColor(accentColor)
        } else {
            val duration = 600L
            
            root?.let { r ->
                val currentBg = (r.background as? ColorDrawable)?.color ?: bgColor
                ValueAnimator.ofObject(ArgbEvaluator(), currentBg, bgColor).apply {
                    this.duration = duration
                    addUpdateListener { r.setBackgroundColor(it.animatedValue as Int) }
                    start()
                }
            }

            appBar?.let { ab ->
                val currentAppBar = (ab.background as? ColorDrawable)?.color ?: appBarColor
                ValueAnimator.ofObject(ArgbEvaluator(), currentAppBar, appBarColor).apply {
                    this.duration = duration
                    addUpdateListener { ab.setBackgroundColor(it.animatedValue as Int) }
                    start()
                }
            }
            
            fsv?.setGridColor(gridColor)
            fsv?.setAccentColor(accentColor)
        }
        
        // Generic UI updates
        activity.findViewById<TextView>(R.id.tvHudTitle)?.let {
            it.setTextColor(titleColor)
            it.setShadowLayer(12f, 0f, 0f, titleColor)
        }
        
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        toolbar?.overflowIcon?.setTint(if (isDark) Color.parseColor("#00C0BF") else Color.parseColor("#008080"))

        // Update common labels if they exist
        val labels = listOf(
            "tvOpacityLabel", "tvPlaceholderLabel", "tvOpacityValue", "tvSettingsTitle",
            "tvBehaviorLabel", "tvAutoHideLabel", "tvAutoHideSub", "tvKeepScreenLabel", "tvKeepScreenSub",
            "tvDefaultStartupLabel", "tvDefaultOpacityLabel", "tvPerformanceLabel", "tvHapticLabel", "tvHapticSub",
            "tvSoundEffectsLabel", "tvSoundEffectsSub", "tvAppearanceLabel", "tvThemeModeLabel",
            "tvLibraryTitle", "tvRecentLabel", "tvAlbumsLabel", "tvEmptyAlbumsLabel",
            "tvAlbumTitle", "tvEmptyAlbumLabel1", "tvEmptyAlbumLabel2",
            "tvGestureTitle", "tvSelectAlbumLabel", "tvTimerLabel", "tvCustomTimerLabel", "tvOptionsLabel",
            "tvShuffleLabel", "tvLoopLabel", "tvSessionCompleteLabel", "tvGoodWorkLabel",
            "tvPosesLabel", "tvMinutesLabel", "tvAboutTitle", "tvAboutText1", "tvAboutText2", "tvAboutText3",
            "tvCreatorTitle", "tvCreatorText1", "tvCreatorText2", "tvCreatorText3", "tvCreatorHandle",
            "tvWebTutorialTitle1", "tvWebTutorialText1", "tvWebTutorialTitle2", "tvWebTutorialText2",
            "tvWebTutorialTitle3", "tvWebTutorialText3", "tvWebTutorialText4", "tvWebTutorialDontShow",
            "tvWorkspacesLabel", "tvSpeedSketchTitle", "tvSpeedSketchWelcomeTitle", "tvSpeedSketchWelcomeText"
        )
        labels.forEach { idStr ->
            val id = activity.resources.getIdentifier(idStr, "id", activity.packageName)
            if (id != 0) {
                val tv = activity.findViewById<TextView>(id)
                if (idStr.endsWith("Sub") || idStr == "tvEmptyAlbumsLabel" || idStr.contains("Text") || idStr == "tvWebTutorialDontShow" || idStr == "tvSpeedSketchWelcomeText") {
                    tv?.setTextColor(if (idStr.contains("Text") && !idStr.startsWith("tvWebTutorialText4") && idStr != "tvSpeedSketchWelcomeText") mainContentTextColor else secondaryTextColor)
                    // Edge case for text4 which is extra secondary
                    if (idStr == "tvWebTutorialText4") tv?.setTextColor(secondaryTextColor)
                    if (idStr == "tvSpeedSketchWelcomeText") tv?.setTextColor(secondaryTextColor)
                } else if ((idStr.contains("Title") || idStr.contains("Label")) && !idStr.startsWith("tvAbout") && !idStr.startsWith("tvCreator") && !idStr.startsWith("tvWebTutorial") && idStr != "tvSettingsTitle" && idStr != "tvLibraryTitle") {
                    tv?.setTextColor(titleColor)
                } else if (idStr.startsWith("tvWebTutorialTitle") || idStr == "tvSpeedSketchWelcomeTitle") {
                    tv?.setTextColor(titleColor)
                } else {
                    tv?.setTextColor(textColor)
                }
            }
        }

        // Update specific accent elements (dividers, specific buttons)
        val accentIds = listOf("vRecentDivider", "vAlbumsDivider", "vWorkspacesDivider")
        accentIds.forEach { idStr ->
            val id = activity.resources.getIdentifier(idStr, "id", activity.packageName)
            if (id != 0) {
                activity.findViewById<View>(id)?.setBackgroundColor(if (isDark) Color.parseColor("#00C0BF") else Color.parseColor("#008080"))
            }
        }
        
        val accentButtons = listOf("btnNewAlbum")
        accentButtons.forEach { idStr ->
            val id = activity.resources.getIdentifier(idStr, "id", activity.packageName)
            if (id != 0) {
                val tv = activity.findViewById<TextView>(id)
                tv?.setTextColor(if (isDark) Color.parseColor("#00C0BF") else Color.parseColor("#008080"))
            }
        }
    }
}
