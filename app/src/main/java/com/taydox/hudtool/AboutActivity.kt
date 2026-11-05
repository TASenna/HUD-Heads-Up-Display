package com.taydox.hudtool

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)

        toolbar.setNavigationOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnInstagram).setOnClickListener {
            openUrl("https://www.instagram.com/_digital.fart_/")
        }

        findViewById<MaterialButton>(R.id.btnInstagramOG).setOnClickListener {
            openUrl("https://www.instagram.com/taydo_x/")
        }

        findViewById<MaterialButton>(R.id.btnCommission).setOnClickListener {
            openUrl("https://digiartsbyr.etsy.com/listing/14384773135")
        }

        ThemeUtils.applyTheme(this, true)
        ThemeUtils.updateProBadge(this)

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

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}