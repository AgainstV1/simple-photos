package app.simplephotos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one screen most people will ever see: every photo, newest first, grouped
 * by day. Tap a photo to open it full screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var albumsButton: Button
    private lateinit var grid: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progress: View
    private lateinit var permissionPanel: View
    private lateinit var permissionButton: Button
    private lateinit var permissionText: TextView

    private lateinit var adapter: PhotoAdapter

    private var album: String? = null
    private var photos: List<Photo> = emptyList()
    private var askedOnce = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (Permissions.granted(this)) {
                reload()
            } else {
                askedOnce = true
                showPermissionPanel(denied = true)
            }
        }

    private val albumsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val chosen = result.data?.getStringExtra(AlbumsActivity.EXTRA_CHOSEN) ?: return@registerForActivityResult
            album = if (chosen == AlbumsActivity.ALL_PHOTOS) null else chosen
            reload()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        titleView = findViewById(R.id.title)
        albumsButton = findViewById(R.id.albumsButton)
        grid = findViewById(R.id.grid)
        emptyText = findViewById(R.id.emptyText)
        progress = findViewById(R.id.progress)
        permissionPanel = findViewById(R.id.permissionPanel)
        permissionButton = findViewById(R.id.permissionButton)
        permissionText = findViewById(R.id.permissionText)

        adapter = PhotoAdapter(this) { index -> ViewerActivity.start(this, album, index) }
        grid.layoutManager = GridLayoutManager(this, PhotoAdapter.COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = adapter.spanSize(position)
            }
        }
        grid.adapter = adapter
        grid.setHasFixedSize(false)

        albumsButton.setOnClickListener {
            if (album == null) {
                albumsLauncher.launch(Intent(this, AlbumsActivity::class.java))
            } else {
                album = null
                reload()
            }
        }

        permissionButton.setOnClickListener {
            if (askedOnce && !Permissions.granted(this)) {
                openAppSettings()
            } else {
                permissionLauncher.launch(Permissions.toRequest())
            }
        }

        if (Permissions.granted(this)) reload() else showPermissionPanel(denied = false)
    }

    override fun onResume() {
        super.onResume()
        if (Permissions.granted(this) && photos.isEmpty()) reload()
    }

    private fun reload() {
        permissionPanel.visibility = View.GONE
        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        titleView.text = album ?: getString(R.string.all_photos)
        albumsButton.text = if (album == null) getString(R.string.albums) else getString(R.string.all_photos)

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { PhotoStore.query(this@MainActivity, album) }
            photos = loaded
            adapter.submit(loaded)
            progress.visibility = View.GONE
            emptyText.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
            grid.scrollToPosition(0)
        }
    }

    private fun showPermissionPanel(denied: Boolean) {
        permissionPanel.visibility = View.VISIBLE
        progress.visibility = View.GONE
        grid.visibility = View.GONE
        emptyText.visibility = View.GONE
        permissionText.text = getString(
            if (denied) R.string.permission_denied else R.string.permission_explain
        )
        permissionButton.text = getString(
            if (denied) R.string.open_settings else R.string.allow_photos
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}
