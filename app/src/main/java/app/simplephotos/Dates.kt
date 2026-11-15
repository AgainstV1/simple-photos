package app.simplephotos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Friendly day headings: Today, Yesterday, then "14 November 2026". */
object Dates {

    private val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    fun heading(timestamp: Long): String {
        val today = Calendar.getInstance()
        val that = Calendar.getInstance().apply { timeInMillis = timestamp }

        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        if (sameDay(today, that)) return "Today"
        today.add(Calendar.DAY_OF_YEAR, -1)
        if (sameDay(today, that)) return "Yesterday"
        return dayFormat.format(Date(timestamp))
    }

    /** Short "14 Nov 2026, 6:32 pm" line shown in the viewer. */
    fun full(timestamp: Long): String =
        SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(timestamp))
}

/** Photo permission, across the three eras of Android. */
object Permissions {

    fun granted(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            has(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                has(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        Build.VERSION.SDK_INT >= 33 -> has(context, Manifest.permission.READ_MEDIA_IMAGES)
        else -> has(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun toRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
