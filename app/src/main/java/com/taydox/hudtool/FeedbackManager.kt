package com.taydox.hudtool

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object FeedbackManager {
    private const val PREFS_NAME = "FeedbackPrefs"
    private const val KEY_TOTAL_MINUTES = "totalUsageMinutes"
    private const val KEY_TOTAL_LAUNCHES = "totalLaunches"
    private const val KEY_CONSECUTIVE_DAYS = "consecutiveDays"
    private const val KEY_LAST_DATE = "lastLaunchDate"
    private const val KEY_HAS_REVIEWED = "hasReviewed"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordLaunch(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_HAS_REVIEWED, false)) return

        val launches = prefs.getInt(KEY_TOTAL_LAUNCHES, 0) + 1
        prefs.edit().putInt(KEY_TOTAL_LAUNCHES, launches).apply()

        // Handle consecutive days
        val lastDateStr = prefs.getString(KEY_LAST_DATE, "")
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        if (lastDateStr != todayStr) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DATE, -1)
            val yesterdayStr = sdf.format(calendar.time)

            var consecutive = prefs.getInt(KEY_CONSECUTIVE_DAYS, 0)
            if (lastDateStr == yesterdayStr) {
                consecutive++
            } else {
                consecutive = 1
            }
            prefs.edit()
                .putInt(KEY_CONSECUTIVE_DAYS, consecutive)
                .putString(KEY_LAST_DATE, todayStr)
                .apply()
        }
    }

    fun addUsageMinutes(context: Context, minutes: Long) {
        val prefs = getPrefs(context)
        val total = prefs.getLong(KEY_TOTAL_MINUTES, 0L) + minutes
        prefs.edit().putLong(KEY_TOTAL_MINUTES, total).apply()
    }

    fun checkAndShowFeedback(activity: AppCompatActivity) {
        val prefs = getPrefs(activity)
        if (prefs.getBoolean(KEY_HAS_REVIEWED, false)) return

        val launches = prefs.getInt(KEY_TOTAL_LAUNCHES, 0)
        val minutes = prefs.getLong(KEY_TOTAL_MINUTES, 0L)
        val consecutive = prefs.getInt(KEY_CONSECUTIVE_DAYS, 0)

        var triggerMessage: String? = null

        // Trigger logic - prioritizes longer engagement
        when {
            consecutive >= 7 -> triggerMessage = "This is your ${consecutive}th consecutive day using HUD! Your commitment to your art is incredible."
            minutes >= 300 -> triggerMessage = "You've used HUD overlays for over ${minutes / 60} hours of focused work! We're glad to be part of your process."
            launches >= 50 -> triggerMessage = "You've launched over $launches reference overlays! You're officially a power user."
            consecutive == 3 -> triggerMessage = "Day 3! HUD is becoming a regular part of your workflow."
            minutes >= 60 -> triggerMessage = "You've spent over an hour drawing with HUD overlays. Congrats on the progress!"
        }

        if (triggerMessage != null) {
            showCoolFeedbackDialog(activity, triggerMessage)
        }
    }

    private fun showCoolFeedbackDialog(activity: AppCompatActivity, message: String) {
        val themedContext = ContextThemeWrapper(activity, R.style.Theme_HUDReferenceOverlayTool)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_feedback, null)
        
        val dialog = AlertDialog.Builder(themedContext)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.tvFeedbackMessage).text = message
        
        dialogView.findViewById<Button>(R.id.btnReview).setOnClickListener {
            markReviewed(activity)
            dialog.dismiss()
            // Official itch.io rating URL format
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, 
                android.net.Uri.parse("https://taydox.itch.io/hud-reference-overlay-tool/rate"))
            try { activity.startActivity(intent) } catch (e: Exception) {}
        }

        dialogView.findViewById<Button>(R.id.btnFeedback).setOnClickListener {
            markReviewed(activity)
            dialog.dismiss()
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("thetaydox@gmail.com"))
                putExtra(android.content.Intent.EXTRA_SUBJECT, "HUD App Feedback")
                putExtra(android.content.Intent.EXTRA_TEXT, "Hi Taydo,\n\nI'm using HUD ${if (BuildConfig.IS_PREMIUM) "Pro" else "Free"} and I wanted to share some thoughts:\n\n")
            }
            try { activity.startActivity(android.content.Intent.createChooser(intent, "Send Feedback")) } catch (e: Exception) {}
        }

        dialogView.findViewById<TextView>(R.id.tvLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun markReviewed(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_HAS_REVIEWED, true).apply()
    }
}
