package com.devsphere.leafbloom.data.source.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LeafValidator(private val context: Context) {

    private var leafModule: Module? = null

    suspend fun loadModel() {
        if (leafModule != null) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("LeafValidator", "Attempting to load leaf validation model from assets...")
                leafModule = LiteModuleLoader.load(assetFilePath(context, "leaf_nonleaf_model.ptl"))
                Log.d("LeafValidator", "Leaf validation model loaded successfully.")
            } catch (e: IOException) {
                Log.e("LeafValidator", "Error loading leaf validation model", e)
            }
        }
    }

    /**
     * Reuses the loaded model or loads it if not already loaded.
     */
    suspend fun isValidLeaf(bitmap: Bitmap): Boolean {
        if (leafModule == null) {
            loadModel()
        }
        
        if (leafModule == null) {
             Log.e("LeafValidator", "Model failed to load, cannot validate. Defaulting to true.")
             // If validation fails due to model loading error, we might want to default to true 
             // to not block the user, or false. True is safer to avoid blocking false negatives.
             return true
        }

        return withContext(Dispatchers.Default) {
            Log.d("LeafValidator", "Starting validation. Input bitmap size: ${bitmap.width}x${bitmap.height}")

            // Resize to 224x224 as required by the model
            val resizedBitmap = bitmap.scale(224, 224)
            Log.d("LeafValidator", "Bitmap resized to 224x224")

            // Prepare input tensor
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )
            Log.d("LeafValidator", "Input tensor created.")

            val leafOutputTensor = leafModule!!.forward(IValue.from(inputTensor)).toTensor()
            val leafScores = leafOutputTensor.dataAsFloatArray
            
            // Index 0: Leaf, Index 1: Non-Leaf
            Log.d("LeafValidator", "Leaf Model Scores (Leaf: ${leafScores[0]}, Non-Leaf: ${leafScores[1]})")
            
            val isLeaf = leafScores[0] >= leafScores[1]
            if (!isLeaf) {
                Log.w("LeafValidator", "Image rejected: Detected as Non-Leaf.")
            } else {
                Log.d("LeafValidator", "Image accepted: Detected as Leaf.")
            }
            isLeaf
        }
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
}
