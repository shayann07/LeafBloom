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

class RipenessClassifier(private val context: Context) {

    private var ripenessModule: Module? = null

    suspend fun loadModel() {
        if (ripenessModule != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("RipenessClassifier", "Attempting to load ripeness model from assets...")
                ripenessModule = LiteModuleLoader.load(assetFilePath(context, "ripeness_model.ptl"))
                Log.d("RipenessClassifier", "Ripeness model loaded successfully.")
            } catch (e: IOException) {
                Log.e("RipenessClassifier", "Error loading model", e)
            }
        }
    }

    suspend fun predict(bitmap: Bitmap): FloatArray {
        if (ripenessModule == null) {
            Log.e("RipenessClassifier", "Model not loaded, cannot predict.")
            return FloatArray(3) { 0f }
        }

        Log.d("RipenessClassifier", "Starting prediction. Input bitmap size: ${bitmap.width}x${bitmap.height}")

        // Resize to 224x224 as required by the model
        val resizedBitmap = bitmap.scale(224, 224)
        Log.d("RipenessClassifier", "Bitmap resized to 224x224")

        // Prepare input tensor
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        Log.d("RipenessClassifier", "Input tensor created.")

        // Identify the Ripeness
        val outputTensor = ripenessModule!!.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        Log.d("RipenessClassifier", "Ripeness Inference complete. Scores: ${scores.joinToString(", ")}")

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
        const val INDEX_RIPE = 0
        const val INDEX_UNKNOWN = 1
        const val INDEX_UNRIPE = 2
    }
}
