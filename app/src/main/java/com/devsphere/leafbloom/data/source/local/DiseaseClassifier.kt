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

    private var leafModule: Module? = null
    private var diseaseModule: Module? = null

    suspend fun loadModel() {
        if (leafModule != null && diseaseModule != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("DiseaseClassifier", "Attempting to load models from assets...")
                leafModule = LiteModuleLoader.load(assetFilePath(context, "leaf_nonleaf_model.ptl"))
                diseaseModule = LiteModuleLoader.load(assetFilePath(context, "tomato_disease_robust.ptl"))
                Log.d("DiseaseClassifier", "Sequence Models loaded successfully.")
            } catch (e: IOException) {
                Log.e("DiseaseClassifier", "Error loading model", e)
            }
        }
    }

    fun predict(bitmap: Bitmap): FloatArray {
        if (leafModule == null || diseaseModule == null) {
            Log.e("DiseaseClassifier", "Models not loaded, cannot predict.")
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

        // 1. Check if Leaf or Non-Leaf
        val leafOutputTensor = leafModule!!.forward(IValue.from(inputTensor)).toTensor()
        val leafScores = leafOutputTensor.dataAsFloatArray
        
        // Index 0: Leaf, Index 1: Non-Leaf
        Log.d("DiseaseClassifier", "Leaf Model Scores (Leaf: ${leafScores[0]}, Non-Leaf: ${leafScores[1]})")
        
        if (leafScores[1] > leafScores[0]) {
            Log.w("DiseaseClassifier", "Image rejected: Detected as Non-Leaf.")
            // Return high confidence for UNKNOWN (Index 0)
            return floatArrayOf(leafScores[1], 0f, 0f, 0f, 0f)
        }

        // 2. Identify the Disease
        val diseaseOutputTensor = diseaseModule!!.forward(IValue.from(inputTensor)).toTensor()
        val dScores = diseaseOutputTensor.dataAsFloatArray
        Log.d("DiseaseClassifier", "Disease Inference complete. Scores: ${dScores.joinToString(", ")}")

        // Map the 4-class output to the 5-element array expected by the app.
        // App Mapping: 0: UNKNOWN, 1: EARLY_BLIGHT, 2: HEALTHY, 3: LATE_BLIGHT, 4: SEPTORIA
        // Model Mapping: 0: Early Blight, 1: Healthy, 2: Late Blight, 3: Septoria
        return floatArrayOf(0f, dScores[0], dScores[1], dScores[2], dScores[3])
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
