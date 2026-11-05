package com.taydox.hudtool

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import coil.load
import coil.decode.VideoFrameDecoder
import coil.ImageLoader
import coil.request.videoFrameMillis

class LibraryActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var llRecentImages: LinearLayout
    private lateinit var rvAlbums: RecyclerView
    private lateinit var llEmptyAlbums: LinearLayout
    
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_RECENT = "recentImages"
    private val KEY_ALBUMS = "savedAlbums"

    data class Album(val name: String, val imageUris: MutableList<String> = mutableListOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        llRecentImages = findViewById(R.id.llRecentImages)
        rvAlbums       = findViewById(R.id.rvAlbums)
        llEmptyAlbums  = findViewById(R.id.llEmptyAlbums)

        rvAlbums.layoutManager = LinearLayoutManager(this)
        rvAlbums.isNestedScrollingEnabled = false

        findViewById<TextView>(R.id.btnNewAlbum).setOnClickListener {
            showNewAlbumDialog()
        }

        loadRecentImages()
        loadAlbums()

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
        loadAlbums()
        ThemeUtils.applyTheme(this, false)
    }

    private fun loadRecentImages() {
        llRecentImages.removeAllViews()
        val arr = JSONArray(prefs.getString(KEY_RECENT, "[]"))

        if (arr.length() == 0) {
            val empty = TextView(this).apply {
                text = "No recently used images yet."
                setTextColor(0xFF606C75.toInt())
                textSize = 14f
                setPadding(0, 40, 0, 40)
            }
            llRecentImages.addView(empty)
            return
        }

        for (i in 0 until arr.length()) {
            val uriStr = arr.getString(i)
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_recent_image, llRecentImages, false)
            val iv = itemView.findViewById<ImageView>(R.id.ivRecentImage)
            val btnRemove = itemView.findViewById<ImageButton>(R.id.btnRemoveRecent)
            
            val uri = Uri.parse(uriStr)
            val isVideo = contentResolver.getType(uri)?.startsWith("video") == true
            iv.load(uri, imageLoader) {
                if (isVideo) videoFrameMillis(1000)
            }

            btnRemove.setOnClickListener {
                removeRecentImage(uriStr)
                loadRecentImages()
            }

            itemView.setOnLongClickListener {
                showAddToAlbumDialog(uriStr)
                true
            }

            llRecentImages.addView(itemView)
        }
    }

    private fun showAddToAlbumDialog(uriStr: String) {
        val albums = getSavedAlbums()
        if (albums.isEmpty()) {
            Toast.makeText(this, "Create an album first!", Toast.LENGTH_SHORT).show()
            return
        }

        val albumNames = albums.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Add to Album")
            .setItems(albumNames) { _, which ->
                val selectedAlbum = albums[which]
                saveImageToAlbum(selectedAlbum.name, uriStr)
                Toast.makeText(this, "Added to ${selectedAlbum.name}", Toast.LENGTH_SHORT).show()
                loadAlbums()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveImageToAlbum(albumName: String, uriStr: String) {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("name") == albumName) {
                val images = obj.optJSONArray("images") ?: JSONArray()
                
                // Prevent duplicates
                var exists = false
                for (j in 0 until images.length()) {
                    if (images.getString(j) == uriStr) { exists = true; break }
                }
                if (!exists) images.put(uriStr)
                
                obj.put("images", images)
            }
            newArr.put(obj)
        }
        prefs.edit().putString(KEY_ALBUMS, newArr.toString()).apply()
    }

    private fun loadAlbums() {
        val albums = getSavedAlbums()
        if (albums.isEmpty()) {
            llEmptyAlbums.visibility = View.VISIBLE
            rvAlbums.visibility = View.GONE
        } else {
            llEmptyAlbums.visibility = View.GONE
            rvAlbums.visibility = View.VISIBLE
            rvAlbums.adapter = AlbumAdapter(albums)
        }
    }

    private fun showNewAlbumDialog() {
        val input = EditText(this).apply {
            hint = "Album name"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF606C75.toInt())
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("New Album")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveNewAlbum(name)
                    Toast.makeText(this, "Album '$name' created!", Toast.LENGTH_SHORT).show()
                    loadAlbums()
                } else {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSavedAlbums(): List<Album> {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val images = obj.optJSONArray("images") ?: JSONArray()
            Album(obj.getString("name"),
                (0 until images.length()).map { images.getString(it) }.toMutableList())
        }
    }

    private fun saveNewAlbum(name: String) {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        arr.put(JSONObject().apply {
            put("name", name)
            put("images", JSONArray())
        })
        prefs.edit().putString(KEY_ALBUMS, arr.toString()).apply()
    }

    private fun removeRecentImage(uriStr: String) {
        val arr = JSONArray(prefs.getString(KEY_RECENT, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getString(i) != uriStr) newArr.put(arr.getString(i))
        }
        prefs.edit().putString(KEY_RECENT, newArr.toString()).apply()
    }

    private fun deleteAlbum(name: String) {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("name") != name) {
                newArr.put(obj)
            }
        }
        prefs.edit().putString(KEY_ALBUMS, newArr.toString()).apply()
        loadAlbums()
    }

    inner class AlbumAdapter(private val albums: List<Album>) :
        RecyclerView.Adapter<AlbumAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName       : TextView  = v.findViewById(R.id.tvAlbumName)
            val tvCount      : TextView  = v.findViewById(R.id.tvAlbumCount)
            val ivThumb1     : ImageView = v.findViewById(R.id.ivThumb1)
            val ivThumb2     : ImageView = v.findViewById(R.id.ivThumb2)
            val ivThumb3     : ImageView = v.findViewById(R.id.ivThumb3)
            val ivThumb4     : ImageView = v.findViewById(R.id.ivThumb4)
            val tvPlaceholder: TextView  = v.findViewById(R.id.tvAlbumPlaceholder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_album, parent, false))

        override fun getItemCount() = albums.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val album = albums[position]
            holder.tvName.text  = album.name
            holder.tvCount.text = "${album.imageUris.size} image${if (album.imageUris.size != 1) "s" else ""}"

            val thumbs = listOf(holder.ivThumb1, holder.ivThumb2, holder.ivThumb3, holder.ivThumb4)

            if (album.imageUris.isEmpty()) {
                thumbs.forEach { it.visibility = View.GONE }
                holder.tvPlaceholder.visibility = View.VISIBLE
            } else {
                holder.tvPlaceholder.visibility = View.GONE
                thumbs.forEachIndexed { index, iv ->
                    if (index < album.imageUris.size) {
                        iv.visibility = View.VISIBLE
                        val uri = Uri.parse(album.imageUris[index])
                        val isVideo = holder.itemView.context.contentResolver.getType(uri)?.startsWith("video") == true
                        iv.load(uri, imageLoader) {
                            if (isVideo) videoFrameMillis(1000)
                        }
                    } else {
                        iv.visibility = View.GONE
                    }
                }
            }

            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(holder.itemView.context, AlbumDetailActivity::class.java).apply {
                        putExtra("ALBUM_NAME", album.name)
                    }
                )
            }

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@LibraryActivity)
                    .setTitle("Delete Album")
                    .setMessage("Are you sure you want to delete '${album.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteAlbum(album.name)
                        Toast.makeText(this@LibraryActivity, "Album deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
    }
}