package com.devsphere.leafbloom.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ResultExporter {

    private const val A4_WIDTH_PT = 595
    private const val PDF_MARGIN_PT = 24
    private const val EXPORT_SUBDIR = "exports"
    private const val GALLERY_SUBDIR = "LeafBloom"

    /**
     * Composes a title header + full content into a single bitmap. Must be called on the
     * main thread (View.draw requires main thread).
     *
     * The header mirrors the screen's title row: background colour + centred bold title text.
     * [headerHeightPx] should be the height of the on-screen back-button row (btnBack.height)
     * plus top/bottom padding so it matches what the user sees.
     */
    fun captureResultScreen(
        context: Context,
        contentView: View,
        titleText: String,
        titleTextSizePx: Float,
        titleTypeface: Typeface?,
        titleTextColor: Int,
        headerHeightPx: Int
    ): Bitmap {
        val width = contentView.width.coerceAtLeast(1)
        val bgColor = resolveBackgroundColor(context)

        // Capture full scroll content (off-screen rows included because contentContainer
        // is wrap_content inside NestedScrollView and is fully measured at its natural height).
        val contentHeight = contentView.height.coerceAtLeast(1)
        val contentBitmap = Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
        Canvas(contentBitmap).apply {
            drawColor(bgColor)
            contentView.draw(this)
        }

        // Build composite bitmap: header row + content
        val totalHeight = headerHeightPx + contentHeight
        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)

        // Draw title centred in the header row
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = titleTextSizePx
            typeface = titleTypeface ?: Typeface.DEFAULT_BOLD
            color = titleTextColor
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val textY = headerHeightPx / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(titleText, width / 2f, textY, textPaint)

        // Draw full content below header
        canvas.drawBitmap(contentBitmap, 0f, headerHeightPx.toFloat(), null)
        contentBitmap.recycle()

        return result
    }

    /**
     * Saves [bitmap] as PNG to Pictures/LeafBloom/ via MediaStore. Returns the inserted Uri.
     * Call on a background thread. On Q+ no storage permission is needed; API 28 requires
     * WRITE_EXTERNAL_STORAGE (caller is responsible for requesting it first).
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Result<Uri> =
        runCatching {
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val now = System.currentTimeMillis() / 1000
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, now)
                put(MediaStore.Images.Media.DATE_MODIFIED, now)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + GALLERY_SUBDIR
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        GALLERY_SUBDIR
                    ).apply { if (!exists()) mkdirs() }
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
                }
            }

            val uri = resolver.insert(collection, values)
                ?: error("MediaStore insert returned null")

            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("Bitmap.compress returned false")
                }
            } ?: error("Could not open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }

    /**
     * Renders [bitmap] into a SINGLE-PAGE PDF with A4 width (595pt) and proportional height.
     * The content is never split across pages. Call on a background thread.
     */
    fun renderBitmapToPdf(context: Context, bitmap: Bitmap, fileName: String): Result<File> =
        runCatching {
            val outDir = File(context.cacheDir, EXPORT_SUBDIR).apply { if (!exists()) mkdirs() }
            val outFile = File(outDir, fileName)

            val usableWidth = A4_WIDTH_PT - 2 * PDF_MARGIN_PT
            val scale = usableWidth.toFloat() / bitmap.width.toFloat()
            val scaledHeight = (bitmap.height * scale).toInt()
            val pageWidth = A4_WIDTH_PT
            val pageHeight = scaledHeight + 2 * PDF_MARGIN_PT

            val document = PdfDocument()
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            try {
                val pageInfo = PdfDocument.PageInfo
                    .Builder(pageWidth, pageHeight, 1)
                    .create()
                val page = document.startPage(pageInfo)
                val dst = RectF(
                    PDF_MARGIN_PT.toFloat(),
                    PDF_MARGIN_PT.toFloat(),
                    (PDF_MARGIN_PT + usableWidth).toFloat(),
                    (PDF_MARGIN_PT + scaledHeight).toFloat()
                )
                page.canvas.drawBitmap(bitmap, null, dst, paint)
                document.finishPage(page)
                FileOutputStream(outFile).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            outFile
        }

    /**
     * Wraps [bitmap] with [paddingPx] of [bgColor] on all sides. Used to give the PNG
     * the same framed look as the PDF (which has natural margin from PDF_MARGIN_PT).
     */
    fun wrapWithPadding(bitmap: Bitmap, paddingPx: Int, bgColor: Int): Bitmap {
        val w = bitmap.width + paddingPx * 2
        val h = bitmap.height + paddingPx * 2
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(bgColor)
        canvas.drawBitmap(bitmap, paddingPx.toFloat(), paddingPx.toFloat(), null)
        return result
    }

    fun buildShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveBackgroundColor(context: Context): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            tv.data
        } else {
            android.graphics.Color.WHITE
        }
    }
}
