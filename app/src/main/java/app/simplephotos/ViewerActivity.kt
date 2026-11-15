package app.simplephotos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen photo. Swipe left and right between photos, pinch to zoom,
 * tap to hide the buttons, share with the share icon.
 */
class ViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ALBUM = "album"
        private const val EXTRA_INDEX = "index"

        fun start(context: Context, album: String?, index: Int) {
            context.startActivity(
                Intent(context, ViewerActivity::class.java)
                    .putExtra(EXTRA_ALBUM, album)
                    .putExtra(EXTRA_INDEX, index)
            )
        }
    }

    private lateinit var pager: ViewPager2
    private lateinit var chrome: View
    private lateinit var caption: TextView
    private lateinit var progress: ProgressBar
    private var photos: List<Photo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        pager = findViewById(R.id.pager)
        chrome = findViewById(R.id.chrome)
        caption = findViewById(R.id.caption)
        progress = findViewById(R.id.progress)

        val album = intent.getStringExtra(EXTRA_ALBUM)
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.shareButton).setOnClickListener { shareCurrent() }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                photos.getOrNull(position)?.let { caption.text = Dates.full(it.takenAt) }
            }
        })

        lifecycleScope.launch {
            photos = withContext(Dispatchers.IO) { PhotoStore.query(this@ViewerActivity, album) }
            if (photos.isEmpty()) {
                finish()
                return@launch
            }
            pager.adapter = PageAdapter(photos) { toggleChrome() }
            pager.setCurrentItem(startIndex.coerceIn(0, photos.lastIndex), false)
            caption.text = Dates.full(photos[startIndex.coerceIn(0, photos.lastIndex)].takenAt)
            progress.visibility = View.GONE
        }
    }

    private fun toggleChrome() {
        chrome.visibility = if (chrome.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun shareCurrent() {
        val photo = photos.getOrNull(pager.currentItem) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, photo.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }
}

private class PageAdapter(
    private val photos: List<Photo>,
    private val onTap: () -> Unit
) : RecyclerView.Adapter<PageAdapter.Holder>() {

    inner class Holder(val frame: FrameLayout, val image: ZoomImageView) :
        RecyclerView.ViewHolder(frame)

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val frame = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        val image = ZoomImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { onTap() }
        }
        frame.addView(image)
        return Holder(frame, image)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val photo = photos[position]
        holder.image.setImageBitmap(null)
        holder.image.resetZoom()
        FullImage.load(holder.frame.context, photo, 2400) { bitmap ->
            if (holder.bindingAdapterPosition == position) holder.image.setImageBitmap(bitmap)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.image.setImageBitmap(null)
        holder.image.resetZoom()
    }
}
