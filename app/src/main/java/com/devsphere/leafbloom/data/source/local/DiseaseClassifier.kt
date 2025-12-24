package com.devsphere.leafbloom.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.graphics.scale

class DiseaseClassifier(context: Context) {

    private var module: Module? = null

    init {
        try {
            Log.d("DiseaseClassifier", "Attempting to load model from assets...")
            module = LiteModuleLoader.load(assetFilePath(context, "tomato_disease_mobile_final.ptl"))
            Log.d("DiseaseClassifier", "Model loaded successfully.")
        } catch (e: IOException) {
            Log.e("DiseaseClassifier", "Error loading model", e)
        }
    }

    fun predict(bitmap: Bitmap): FloatArray {
        if (module == null) {
            Log.e("DiseaseClassifier", "Model not loaded, cannot predict.")
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
        val outputTensor = module!!.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        Log.d("DiseaseClassifier", "Inference complete. Scores: ${scores.joinToString(", ")}")

        // NOT applying Softmax here as it is already baked into the model
        return scores
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
