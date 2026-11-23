package com.massspx.videofaceextractor

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var chooseButton: Button
    private lateinit var runButton: Button
    private lateinit var shareButton: Button
    private var selectedVideo: Uri? = null
    private var latestZip: File? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            status.text = "Video ရွေးပြီးပါပြီ။ Extract ကိုနှိပ်ပါ။"
            runButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(22))
            setBackgroundColor(Color.rgb(12, 17, 28))
        }
        val heading = TextView(this).apply {
            text = "VIDEO FACE EXTRACTOR"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Video ထဲက မျက်နှာအားလုံးကို ရှာဖွေပြီး ပုံအဖြစ် ထုတ်ယူပါ"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(24))
        }
        chooseButton = makeButton("1. Choose Video")
        runButton = makeButton("2. Extract Faces").apply { isEnabled = false }
        shareButton = makeButton("3. Share ZIP").apply { isEnabled = false }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        status = TextView(this).apply {
            text = "စတင်ရန် Video တစ်ခုရွေးပါ။"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(18))
        }
        val note = TextView(this).apply {
            text = "Balanced mode • 0.8 sec/frame • JPG + Contact Sheet + ZIP\nVideo ကို ဖုန်းပေါ်မှာပဲ စစ်ဆေးပါတယ်။ Server သို့ မတင်ပါ။"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        root.addView(heading)
        root.addView(subtitle)
        root.addView(chooseButton, matchWrap())
        root.addView(runButton, matchWrap())
        root.addView(progress, matchWrap())
        root.addView(status, matchWrap())
        root.addView(shareButton, matchWrap())
        root.addView(note, matchWrap())
        setContentView(root)

        chooseButton.setOnClickListener { videoPicker.launch(arrayOf("video/*")) }
        runButton.setOnClickListener { startExtraction() }
        shareButton.setOnClickListener { shareZip() }
    }

    private fun startExtraction() {
        val uri = selectedVideo ?: return
        chooseButton.isEnabled = false
        runButton.isEnabled = false
        shareButton.isEnabled = false
        progress.progress = 0
        status.text = "Video ကို စစ်ဆေးနေပါတယ်..."
        worker.execute {
            try {
                val result = extract(uri)
                runOnUiThread {
                    latestZip = result.zip
                    progress.progress = 100
                    status.text = "ပြီးပါပြီ။\nတွေ့ရှိသော မျက်နှာပုံ: ${result.count}\nGallery ထဲတွင် JPG နှင့် Contact Sheet သိမ်းပြီးပါပြီ။"
                    shareButton.isEnabled = result.zip.exists()
                    chooseButton.isEnabled = true
                    runButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message ?: "မသိရှိသော ပြဿနာ"}"
                    chooseButton.isEnabled = true
                    runButton.isEnabled = true
                }
            }
        }
    }

    private data class ExtractionResult(val count: Int, val zip: File)

    private fun extract(videoUri: Uri): ExtractionResult {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, videoUri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: throw IllegalStateException("Video duration ကို မဖတ်နိုင်ပါ။")
        if (durationMs <= 0L) throw IllegalStateException("Video အလွတ်ဖြစ်နေပါတယ်။")

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .setMinFaceSize(0.045f)
            .build()
        val detector: FaceDetector = FaceDetection.getClient(options)

        val session = "VFE_" + System.currentTimeMillis()
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), session)
        if (!outputDir.exists()) outputDir.mkdirs()
        val crops = mutableListOf<Bitmap>()
        val hashes = mutableListOf<Long>()
        var saved = 0
        val intervalMs = 800L
        val maxFrames = 3600
        var frameIndex = 0

        try {
            var timeMs = 0L
            while (timeMs < durationMs && frameIndex < maxFrames) {
                val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = downscale(frame, 960)
                    if (scaled !== frame) frame.recycle()
                    try {
                        val faces = Tasks.await(detector.process(InputImage.fromBitmap(scaled, 0)))
                        for (face in faces) {
                            val crop = cropFace(scaled, face.boundingBox) ?: continue
                            val hash = averageHash(crop)
                            val duplicate = hashes.any { java.lang.Long.bitCount(it xor hash) <= 4 }
                            if (duplicate) {
                                crop.recycle()
                                continue
                            }
                            hashes.add(hash)
                            val name = String.format(Locale.US, "face_%04d_%06dms.jpg", saved + 1, timeMs)
                            FileOutputStream(File(outputDir, name)).use {
                                crop.compress(Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            publishImage(crop, name, "Detected face at ${timeMs}ms")
                            crops.add(crop)
                            saved++
                        }
                    } finally {
                        scaled.recycle()
                    }
                }
                frameIndex++
                val pct = min(99, ((timeMs.toDouble() / durationMs) * 100).toInt())
                runOnUiThread {
                    progress.progress = pct
                    status.text = "စစ်ဆေးနေသည်... ${pct}%\nFrame: $frameIndex • မျက်နှာပုံ: $saved"
                }
                timeMs += intervalMs
            }
            if (saved == 0) throw IllegalStateException("မျက်နှာ မတွေ့ပါ။ ပိုကြည်လင်သော Video ကို စမ်းကြည့်ပါ။")

            val sheet = makeContactSheet(crops)
            val sheetName = "contact_sheet.jpg"
            FileOutputStream(File(outputDir, sheetName)).use { sheet.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            publishImage(sheet, session + "_contact_sheet.jpg", "Video Face Extractor contact sheet")
            sheet.recycle()

            val zipFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), session + ".zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                outputDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            crops.forEach { it.recycle() }
            return ExtractionResult(saved, zipFile)
        } finally {
            detector.close()
            retriever.release()
        }
    }

    private fun downscale(source: Bitmap, maxSide: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxSide) return source
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(source, (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun cropFace(bitmap: Bitmap, bounds: Rect): Bitmap? {
        val padX = (bounds.width() * 0.18f).toInt()
        val padY = (bounds.height() * 0.22f).toInt()
        val left = (bounds.left - padX).coerceIn(0, bitmap.width - 1)
        val top = (bounds.top - padY).coerceIn(0, bitmap.height - 1)
        val right = (bounds.right + padX).coerceIn(left + 1, bitmap.width)
        val bottom = (bounds.bottom + padY).coerceIn(top + 1, bitmap.height)
        if (right - left < 24 || bottom - top < 24) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun averageHash(bitmap: Bitmap): Long {
        val tiny = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var total = 0L
        val gray = IntArray(64)
        var index = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val c = tiny.getPixel(x, y)
            val value = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            gray[index++] = value
            total += value
        }
        tiny.recycle()
        val average = total / 64
        var hash = 0L
        for (i in gray.indices) if (gray[i] >= average) hash = hash or (1L shl i)
        return hash
    }

    @Suppress("DEPRECATION")
    private fun publishImage(bitmap: Bitmap, name: String, description: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VideoFaceExtractor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Gallery ထဲ သိမ်းမရပါ။")
        try {
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                ?: throw IllegalStateException("Image output stream မရပါ။")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun makeContactSheet(images: List<Bitmap>): Bitmap {
        val cell = 180
        val columns = 4
        val rows = (images.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(columns * cell, rows * cell, Bitmap.Config.RGB_565)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(24, 29, 40))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        images.forEachIndexed { index, bitmap ->
            val col = index % columns
            val row = index / columns
            val scale = min((cell - 12).toFloat() / bitmap.width, (cell - 12).toFloat() / bitmap.height)
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            val left = col * cell + (cell - w) / 2
            val top = row * cell + (cell - h) / 2
            canvas.drawBitmap(bitmap, null, Rect(left, top, left + w, top + h), paint)
        }
        return sheet
    }

    private fun shareZip() {
        val file = latestZip ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, packageName + ".provider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share extracted faces"))
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.btn_default)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(12) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
