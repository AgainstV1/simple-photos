package app.simplephotos

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A row is either a big day heading or a photo tile. */
sealed class Row {
    data class Heading(val label: String) : Row()
    data class Tile(val photo: Photo, val index: Int) : Row()
}

/**
 * Grid of photos with day headings in between. Rows are built by hand rather
 * than inflated from XML so the tile size can follow the screen width exactly.
 */
class PhotoAdapter(
    private val context: Context,
    private val onOpen: (index: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = ArrayList<Row>()
    private val tilePx = context.resources.displayMetrics.widthPixels / COLUMNS
    private val gap = (context.resources.displayMetrics.density * 2).toInt()

    fun submit(photos: List<Photo>) {
        rows.clear()
        var currentHeading: String? = null
        photos.forEachIndexed { index, photo ->
            val heading = Dates.heading(photo.takenAt)
            if (heading != currentHeading) {
                rows.add(Row.Heading(heading))
                currentHeading = heading
            }
            rows.add(Row.Tile(photo, index))
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    /** Day headings take the whole row; photos take one cell. */
    fun spanSize(position: Int) = if (rows[position] is Row.Heading) COLUMNS else 1

    override fun getItemViewType(position: Int) =
        if (rows[position] is Row.Heading) TYPE_HEADING else TYPE_TILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADING) HeadingHolder(newHeading()) else TileHolder(newTile())

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Heading -> (holder as HeadingHolder).text.text = row.label
            is Row.Tile -> (holder as TileHolder).bind(row)
        }
    }

    private fun newHeading(): TextView = TextView(context).apply {
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(gap * 6, gap * 10, gap * 4, gap * 4)
        textSize = 17f
        setTextColor(context.getColor(R.color.text_secondary))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun newTile(): FrameLayout = FrameLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(tilePx, tilePx)
        setPadding(gap, gap, gap, gap)
        setBackgroundColor(context.getColor(R.color.bg))
        isClickable = true
        isFocusable = true
    }

    inner class HeadingHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    inner class TileHolder(private val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        private val image: ImageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.getColor(R.color.tile_placeholder))
            contentDescription = "Photo"
        }

        private var boundId: Long = -1

        init {
            frame.addView(image)
            frame.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val row = rows[position]
                    if (row is Row.Tile) onOpen(row.index)
                }
            }
        }

        fun bind(row: Row.Tile) {
            boundId = row.photo.id
            image.setImageDrawable(null)
            frame.setBackgroundColor(context.getColor(R.color.tile_placeholder))
            Thumbs.load(context, row.photo, tilePx) { bitmap ->
                if (boundId == row.photo.id && bitmap != null) image.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private const val TYPE_HEADING = 0
        private const val TYPE_TILE = 1
        const val COLUMNS = 3
    }
}
