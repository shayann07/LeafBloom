package com.devsphere.leafbloom.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.graphics.scale

class DiseaseClassifier(private val context: Context) {

    private var module: Module? = null

    suspend fun loadModel() {
        if (module != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("DiseaseClassifier", "Attempting to load model from assets...")
                module = LiteModuleLoader.load(assetFilePath(context, "tomato_disease_mobile_final.ptl"))
                Log.d("DiseaseClassifier", "Model loaded successfully.")
            } catch (e: IOException) {
                Log.e("DiseaseClassifier", "Error loading model", e)
            }
        }
    }

    fun predict(bitmap: Bitmap): FloatArray {
        if (module == null) {
            Log.e("DiseaseClassifier", "Model not loaded, cannot predict.")
            // Return zeros or handle error upstream. 
            // In a real app we might want to throw or return a specific error state.
            return floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }

        Log.d("DiseaseClassifier", "Starting prediction. Input bitmap size: ${bitmap.width}x${bitmap.height}")

        // Resize to 224x224 as required by the model
        val resizedBitmap = bitmap.scale(224, 224)
        Log.d("DiseaseClassifier", "Bitmap resized to 224x224")

        // Prepare input tensor
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        Log.d("DiseaseClassifier", "Input tensor created.")

        // Run inference
        if (!isPlantLike(resizedBitmap)) {
            Log.w("DiseaseClassifier", "Image rejected: Not enough plant-like content.")
            // Return high confidence for UNKNOWN (Index 0), and 0 for others.
            return floatArrayOf(1.0f, 0f, 0f, 0f, 0f)
        }

        val outputTensor = module!!.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        Log.d("DiseaseClassifier", "Inference complete. Scores: ${scores.joinToString(", ")}")

        // NOT applying Softmax here as it is already baked into the model
        return scores
    }

    /**
     * Heuristic check: Does the image contain enough Green/Yellow/Brown pixels?
     * This filters out random objects like fans, keyboards, walls, etc.
     */
    private fun isPlantLike(bitmap: Bitmap): Boolean {
        var plantPixelCount = 0
        val totalPixels = bitmap.width * bitmap.height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val hsv = FloatArray(3)
        // HSV Ranges for Plant-like colors (Green, Yellow, Brown/Orange)
        // Hue is [0 .. 360)
        // Green: ~60-180
        // Yellow: ~40-60
        // Brown/Orange: ~10-40 (often low brightness/saturation, but checking Hue covers the base)
        
        // We generally look for Hue between 25 and 185
        // Saturation should be > 10% (avoid pure greys)
        // Value/Brightness should be > 15% (avoid pitch black)

        for (pixel in pixels) {
            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val valBr = hsv[2]

            // Conservative Check:
            // Hue: 25 (Brownish/Orange) to 185 (Cyan/Dark Green)
            // Sat: > 0.15 (Not grey)
            // Val: > 0.20 (Not black)
            if (hue in 25f..185f && sat > 0.15f && valBr > 0.20f) {
                plantPixelCount++
            }
        }

        val plantRatio = plantPixelCount.toFloat() / totalPixels
        Log.d("DiseaseClassifier", "Plant Pixel Ratio: $plantRatio")
        
        // Threshold: at least 5% of the pixels must be plant-like.
        // This is low enough to catch macro shots of dry spots but high enough to filter a white fan.
        return plantRatio > 0.05f 
    }

    @Throws(IOException::class)
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }

    companion object {
        // Classes mapping for reference
        // 0: UNKNOWN
        // 1: EARLY_BLIGHT
        // 2: HEALTHY
        // 3: LATE_BLIGHT
        // 4: SEPTORIA
        const val INDEX_UNKNOWN = 0
        const val INDEX_EARLY_BLIGHT = 1
        const val INDEX_HEALTHY = 2
        const val INDEX_LATE_BLIGHT = 3
        const val INDEX_SEPTORIA = 4
    }
}
