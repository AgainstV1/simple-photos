package app.simplephotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A plain list of folders: name, number of photos, and a cover picture. */
class AlbumsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHOSEN = "chosen_album"
        const val ALL_PHOTOS = "__all__"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_albums)

        val list = findViewById<RecyclerView>(R.id.albumList)
        val progress = findViewById<View>(R.id.progress)
        val back = findViewById<View>(R.id.backButton)

        back.setOnClickListener { finish() }

        list.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                PhotoStore.albumsOf(PhotoStore.query(this@AlbumsActivity))
            }
            progress.visibility = View.GONE
            list.adapter = AlbumAdapter(this@AlbumsActivity, albums) { chosen ->
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(EXTRA_CHOSEN, chosen?.name ?: ALL_PHOTOS)
                )
                finish()
            }
        }
    }
}

private class AlbumAdapter(
    private val context: Context,
    private val albums: List<Album>,
    private val onPick: (Album?) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.Holder>() {

    inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    override fun getItemCount() = albums.size + 1 // +1 for "All photos"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((density * 18).toInt(), (density * 12).toInt(), (density * 18).toInt(), (density * 12).toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            isFocusable = true
        }

        val cover = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((density * 64).toInt(), (density * 64).toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.tile_placeholder))
            contentDescription = null
        }
        row.addView(cover)

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((density * 16).toInt(), 0, 0, 0)
        }
        val name = TextView(context).apply {
            textSize = 18f
            setTextColor(context.getColor(R.color.text_primary))
        }
        val count = TextView(context).apply {
            textSize = 14f
            setTextColor(context.getColor(R.color.text_secondary))
        }
        texts.addView(name)
        texts.addView(count)
        row.addView(texts)

        return Holder(row)
    }

    /** A cache key that is unique per cover picture. */
    private fun pseudoPhoto(uri: android.net.Uri) =
        Photo(uri.toString().hashCode().toLong(), uri, "", "", 0L)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = holder.row
        val cover = row.getChildAt(0) as ImageView
        val texts = row.getChildAt(1) as LinearLayout
        val name = texts.getChildAt(0) as TextView
        val count = texts.getChildAt(1) as TextView

        val album = if (position == 0) null else albums[position - 1]

        if (album == null) {
            name.text = context.getString(R.string.all_photos)
            count.text = context.getString(R.string.all_photos_sub)
            cover.setImageDrawable(null)
            val allCount = albums.sumOf { it.count }
            count.text = "$allCount photos"
            val firstCover = albums.firstOrNull()?.cover
            if (firstCover != null) {
                Thumbs.load(context, pseudoPhoto(firstCover), 200) { bmp -> cover.setImageBitmap(bmp) }
            }
            row.setOnClickListener { onPick(null) }
        } else {
            name.text = album.name
            count.text = "${album.count} photos"
            cover.setImageDrawable(null)
            Thumbs.load(context, pseudoPhoto(album.cover), 200) { bmp ->
                cover.setImageBitmap(bmp)
            }
            row.setOnClickListener { onPick(album) }
        }
    }
}
