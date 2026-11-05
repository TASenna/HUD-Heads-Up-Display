package com.taydox.hudtool

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import coil.load
import coil.decode.VideoFrameDecoder
import coil.ImageLoader
import coil.request.videoFrameMillis

class AlbumDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rvImages: RecyclerView
    private lateinit var llEmpty: LinearLayout
    private lateinit var tvAlbumTitle: TextView
    
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    private val PREFS_NAME = "OverlayPrefs"
    private val KEY_ALBUMS  = "savedAlbums"
    private val KEY_IMAGE_URI = "imageUri"
    private val KEY_IMAGE_PENDING = "pendingImageUri"

    private var albumName = ""

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                addImageToAlbum(uri.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_detail)

        albumName = intent.getStringExtra("ALBUM_NAME") ?: ""
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        tvAlbumTitle = findViewById(R.id.tvAlbumTitle)
        tvAlbumTitle.text = albumName

        rvImages = findViewById(R.id.rvImages)
        llEmpty  = findViewById(R.id.llEmpty)

        rvImages.layoutManager = GridLayoutManager(this, 3)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnAddImage).setOnClickListener {
            pickImageLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                val proActive = ThemeUtils.isPro(this@AlbumDetailActivity)
                type = if (proActive) "*/*" else "image/*"
                if (proActive) {
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        }

        loadImages()
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

    private fun loadImages() {
        val images = getAlbumImages()
        if (images.isEmpty()) {
            llEmpty.visibility  = View.VISIBLE
            rvImages.visibility = View.GONE
        } else {
            llEmpty.visibility  = View.GONE
            rvImages.visibility = View.VISIBLE
            rvImages.adapter = ImageAdapter(images)
        }
    }

    private fun getAlbumImages(): List<String> {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("name") == albumName) {
                val images = obj.optJSONArray("images") ?: return emptyList()
                return (0 until images.length()).map { images.getString(it) }
            }
        }
        return emptyList()
    }

    private fun addImageToAlbum(uriStr: String) {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("name") == albumName) {
                val images = obj.optJSONArray("images") ?: JSONArray()
                images.put(uriStr)
                obj.put("images", images)
                arr.put(i, obj)
                break
            }
        }
        prefs.edit().putString(KEY_ALBUMS, arr.toString()).apply()
        Toast.makeText(this, "Image added to $albumName", Toast.LENGTH_SHORT).show()
        loadImages()
    }

    private fun removeImageFromAlbum(uriStr: String) {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("name") == albumName) {
                val images = obj.optJSONArray("images") ?: JSONArray()
                val newImages = JSONArray()
                for (j in 0 until images.length()) {
                    if (images.getString(j) != uriStr) newImages.put(images.getString(j))
                }
                obj.put("images", newImages)
                arr.put(i, obj)
                break
            }
        }
        prefs.edit().putString(KEY_ALBUMS, arr.toString()).apply()
        loadImages()
    }

    private fun loadImageToMain(uriStr: String) {
        // Add to recently used
        val keyRecent = "recentImages"
        val arr = org.json.JSONArray(prefs.getString(keyRecent, "[]"))
        val newArr = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            if (arr.getString(i) != uriStr) newArr.put(arr.getString(i))
        }
        val final = org.json.JSONArray()
        final.put(uriStr)
        for (i in 0 until newArr.length()) final.put(newArr.getString(i))
        val trimmed = org.json.JSONArray()
        for (i in 0 until minOf(10, final.length())) trimmed.put(final.getString(i))
        prefs.edit()
            .putString(keyRecent, trimmed.toString())
            .putString(KEY_IMAGE_URI, uriStr)
            .putBoolean(KEY_IMAGE_PENDING, true)
            .apply()
        Toast.makeText(this, "Image loaded! Going back...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    inner class ImageAdapter(private val images: List<String>) :
        RecyclerView.Adapter<ImageAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv        : ImageView   = v.findViewById(R.id.ivImage)
            val btnRemove : ImageButton = v.findViewById(R.id.btnRemove)
            val btnLoad   : TextView    = v.findViewById(R.id.btnLoad)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_album_image, parent, false))

        override fun getItemCount() = images.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uriStr = images[position]
            val uri = Uri.parse(uriStr)
            val isVideo = holder.itemView.context.contentResolver.getType(uri)?.startsWith("video") == true
            
            holder.iv.load(uri, imageLoader) {
                if (isVideo) videoFrameMillis(1000)
            }

            holder.btnRemove.setOnClickListener { removeImageFromAlbum(uriStr) }
            holder.btnLoad.setOnClickListener   { loadImageToMain(uriStr) }
        }
    }
}
