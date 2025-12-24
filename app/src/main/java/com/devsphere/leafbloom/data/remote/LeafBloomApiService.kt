package com.devsphere.leafbloom.data.remote

import com.devsphere.leafbloom.data.model.IdentifyResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface LeafBloomApiService {
    /**
     * Calls our Identify Plant Cloud Function (v2).
     *
     * @param image The image file part (key="images").
     * @param organ Optional organ type (leaf, flower, etc.). Default handled by backend.
     */
    @Multipart
    @POST("identifyPlantV2")
    suspend fun identifyPlant(
        @Part image: MultipartBody.Part,
        @Query("organ") organ: String? = "auto" // 'auto' lets the backend decide or default to leaf
    ): Response<IdentifyResponse>
}
