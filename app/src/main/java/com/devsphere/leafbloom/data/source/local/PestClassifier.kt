package com.devsphere.leafbloom.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.LiteModuleLoader
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.graphics.scale

class PestClassifier(private val context: Context) {

    private var pestModule: Module? = null

    suspend fun loadModel() {
        if (pestModule != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("PestClassifier", "Attempting to load pest model from assets...")
                pestModule = LiteModuleLoader.load(assetFilePath(context, "pest_id_model.ptl"))
                Log.d("PestClassifier", "Pest model loaded successfully.")
            } catch (e: IOException) {
                Log.e("PestClassifier", "Error loading model", e)
            }
        }
    }

    suspend fun predict(bitmap: Bitmap): FloatArray {
        if (pestModule == null) {
            Log.e("PestClassifier", "Model not loaded, cannot predict.")
            return FloatArray(13) { 0f }
        }

        Log.d("PestClassifier", "Starting prediction. Input bitmap size: ${bitmap.width}x${bitmap.height}")

        // Resize to 224x224 as required by the model
        val resizedBitmap = bitmap.scale(224, 224)
        Log.d("PestClassifier", "Bitmap resized to 224x224")

        // Prepare input tensor
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        Log.d("PestClassifier", "Input tensor created.")

        // Identify the Pest
        val outputTensor = pestModule!!.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        Log.d("PestClassifier", "Pest Inference complete. Scores: ${scores.joinToString(", ")}")

        // Append 0 for Unknown at the end
        val finalScores = FloatArray(13)
        System.arraycopy(scores, 0, finalScores, 0, scores.size)
        finalScores[12] = 0f // Unknown
        
        return finalScores
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
        const val INDEX_ANTS = 0
        const val INDEX_BEES = 1
        const val INDEX_BEETLE = 2
        const val INDEX_CATTERPILLAR = 3
        const val INDEX_EARTHWORMS = 4
        const val INDEX_EARWIG = 5
        const val INDEX_GRASSHOPPER = 6
        const val INDEX_MOTH = 7
        const val INDEX_SLUG = 8
        const val INDEX_SNAIL = 9
        const val INDEX_WASP = 10
        const val INDEX_WEEVIL = 11
        const val INDEX_UNKNOWN = 12
    }
}
