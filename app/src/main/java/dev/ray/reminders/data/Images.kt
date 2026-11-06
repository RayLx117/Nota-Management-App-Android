package dev.ray.reminders.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID

/** Copies pictures the user picks into app storage, shrunk to a notification-friendly size. */
object Images {
    private const val MAX_WIDTH = 1080

    /** Imports the picked image and returns its stored path, or null if it couldn't be read. Call off the main thread. */
    fun import(ctx: Context, uri: Uri): String? = try {
        val source = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            if (w > MAX_WIDTH) decoder.setTargetSize(MAX_WIDTH, info.size.height * MAX_WIDTH / w)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val dir = File(ctx.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    fun delete(path: String) {
        File(path).delete()
    }
}
