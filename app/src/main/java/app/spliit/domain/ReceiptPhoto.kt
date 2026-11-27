package app.spliit.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * A photograph picked to become a receipt on an expense.
 *
 * It is already the shape that will be uploaded: upright, no larger than the instance needs, and
 * under Spliit's own 5 MB ceiling. The dimensions travel with it because they are part of the
 * document the server stores — the web app sends them, and an expense edited here must not write
 * documents without them.
 */
data class ReceiptPhoto(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg",
    val fileName: String = "receipt.jpg",
)

/**
 * Reads a photo from the picker and makes it fit to upload.
 *
 * Spliit refuses documents above 5 MB, and the camera on a modern phone produces several times
 * that. Rather than tell somebody their photo is too big, the picture is decoded at a sample that
 * lands near [MaxEdge], turned upright (the pixels carry no rotation of their own — only EXIF
 * says which way the phone was held), and re-encoded as JPEG, losing quality only if it still
 * does not fit.
 *
 * The decoding is the expensive part; call this off the main thread.
 */
object ReceiptPhotos {

    /** The ceiling the web client enforces, in bytes. */
    const val MaxBytes = 5 * 1024 * 1024

    /** Long edge a photograph is reduced to before it is sent: enough to read a receipt on. */
    private const val MaxEdge = 1800

    fun read(context: Context, uri: Uri): ReceiptPhoto {
        val resolver = context.contentResolver

        // `decodeStream` answers null by design when it is only asked for bounds, so the stream is
        // checked for on its own and the decode result is ignored: a `?:` after the `use` above
        // would test the decode, not the stream, and reject every photo.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sizing = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("That file couldn’t be opened.")
        sizing.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("That photo couldn’t be read.")
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MaxEdge) sample *= 2

        val pixels = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("That file couldn’t be opened.")
        val decoded = pixels.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } ?: throw IllegalArgumentException("That photo couldn’t be read.")

        var bitmap = upright(context, uri, decoded)
        var quality = 88
        var bytes = compress(bitmap, quality)
        while (bytes.size > MaxBytes && quality > 60) {
            quality -= 8
            bytes = compress(bitmap, quality)
        }
        // Still too big at a sane quality: the photograph is simply larger than a receipt needs.
        while (bytes.size > MaxBytes && max(bitmap.width, bitmap.height) > 600) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                bitmap.width * 3 / 4,
                bitmap.height * 3 / 4,
                true,
            )
            bytes = compress(bitmap, 70)
        }
        if (bytes.size > MaxBytes) throw IllegalArgumentException("That photo is too large to send.")

        return ReceiptPhoto(
            bytes = bytes,
            width = bitmap.width,
            height = bitmap.height,
            fileName = "receipt-${System.currentTimeMillis()}.jpg",
        )
    }

    private fun upright(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}
