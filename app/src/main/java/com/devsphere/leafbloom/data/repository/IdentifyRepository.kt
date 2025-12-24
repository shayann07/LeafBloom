package com.devsphere.leafbloom.data.repository

import android.util.Log
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.remote.LeafBloomApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * IdentifyRepository
 *
 * Handles the communication with the Cloud Function backend.
 * Uses a singleton Retrofit instance for efficiency.
 */
object IdentifyRepository {

    private const val TAG = "IdentifyRepository"
    
    // REPLACE THIS WITH YOUR DEPLOYED URL
    // Ensure it ends with a slash if it was a base path, but here we likely need just the base domain.
    // However, Cloud Functions are weird. 
    // If your URL is https://identifyplantv2-xyz.run.app, that is the ENDPOINT.
    // So Base URL should be the regional base, or we treat the @POST as the full relative path if we hack it.
    // Better strategy: Base URL = https://asia-south1-devsphere-leafbloom.cloudfunctions.net/
    
    private const val BASE_URL = "https://asia-south1-devsphere-leafbloom.cloudfunctions.net/"

    private val apiService: LeafBloomApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS) // Cloud Functions can have cold starts
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LeafBloomApiService::class.java)
    }

    suspend fun identifyPlant(imageFile: File): Result<IdentifyResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Preparing to upload: ${imageFile.name}, size: ${imageFile.length()} bytes")

            // Prepare Multipart Body
            val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("images", imageFile.name, requestFile)

            // Make the call
            val response = apiService.identifyPlant(body)

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                if (result.ok) {
                    Result.success(result)
                } else {
                    // Backend returned logic error (e.g. no plant found)
                    Result.failure(Exception("Identification failed."))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API Error: ${response.code()} - $errorBody")
                Result.failure(Exception("Server returned error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during identification", e)
            Result.failure(e)
        }
    }
}
