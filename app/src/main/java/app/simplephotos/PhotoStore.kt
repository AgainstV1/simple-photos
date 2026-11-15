package app.simplephotos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.util.concurrent.Executors

/** One photo on the device. */
data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val album: String,
    val takenAt: Long
)

/** One folder of photos. */
data class Album(
    val name: String,
    val count: Int,
    val cover: Uri
)

/**
 * All MediaStore access lives here. Read-only: the app never writes, edits or
 * deletes anything on the phone.
 */
object PhotoStore {

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    /** Every photo, newest first. Pass [album] to see a single folder. */
    fun query(context: Context, album: String? = null): List<Photo> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )

        val clauses = mutableListOf("${MediaStore.Images.Media.SIZE} > 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clauses.add("${MediaStore.Images.Media.IS_PENDING} = 0")
        }
        if (album != null) {
            clauses.add("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?")
        }

        val photos = ArrayList<Photo>(512)
        val cursor = context.contentResolver.query(
            collection,
            projection,
            clauses.joinToString(" AND "),
            if (album != null) arrayOf(album) else null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol)
                val added = c.getLong(addedCol) * 1000L
                photos.add(
                    Photo(
                        id = id,
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        name = c.getString(nameCol) ?: "",
                        album = c.getString(albumCol) ?: "Other",
                        takenAt = if (taken > 0L) taken else added
                    )
                )
            }
        }
        photos.sortByDescending { it.takenAt }
        return photos
    }

    /** Folds a photo list into the folders those photos live in, biggest first. */
    fun albumsOf(photos: List<Photo>): List<Album> =
        photos.groupBy { it.album }
            .map { (name, items) -> Album(name, items.size, items.first().uri) }
            .sortedWith(compareByDescending<Album> { it.count }.thenBy { it.name.lowercase() })
}

/**
 * Small thumbnail cache, so scrolling back up the grid never re-decodes anything.
 */
object Thumbs {

    private val cache: LruCache<Long, Bitmap> by lazy {
        val budgetKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<Long, Bitmap>(budgetKb) {
            override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
        }
    }

    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, photo: Photo, sizePx: Int, onReady: (Bitmap?) -> Unit) {
        cache.get(photo.id)?.let { return onReady(it) }
        pool.execute {
            val bitmap = decode(context.contentResolver, photo, sizePx)
            if (bitmap != null) cache.put(photo.id, bitmap)
            main.post { onReady(bitmap) }
        }
    }

    private fun decode(resolver: ContentResolver, photo: Photo, sizePx: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(photo.uri, Size(sizePx, sizePx), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                resolver, photo.id, MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
    } catch (e: Exception) {
        null
    }

    fun clear() = cache.evictAll()
}

/**
 * Loads a big, screen-sized bitmap for the full-screen viewer. Uses ImageDecoder
 * on Android 9+ (it applies the EXIF rotation for us) and a hand-rolled sampled
 * decode with an EXIF fix-up on older versions.
 */
object FullImage {

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, photo: Photo, targetPx: Int, onReady: (Bitmap?) -> Unit) {
        pool.execute {
            val bitmap = try { decode(context, photo, targetPx) } catch (e: Exception) { null }
            main.post { onReady(bitmap) }
        }
    }

    private fun decode(context: Context, photo: Photo, targetPx: Int): Bitmap? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(resolver, photo.uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > targetPx) {
                    val ratio = targetPx.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1)
                    )
                }
            }
        }

        // API 24-27 path.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > targetPx * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = resolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val rotation = try {
            resolver.openInputStream(photo.uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }

        if (rotation == 0f) return raw
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }
}
