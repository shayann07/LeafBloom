package com.devsphere.leafbloom.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Clean data model matching your Cloud Function "identifyPlantV2" response.
 * We only capture what we need for the UI.
 */
@Parcelize
data class IdentifyResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("provider") val provider: String?,
    @SerializedName("data") val data: PlantData?
) : Parcelable

@Parcelize
data class PlantData(
    @SerializedName("bestMatch") val bestMatch: String?,
    @SerializedName("results") val results: List<PlantResult>?
) : Parcelable

@Parcelize
data class PlantResult(
    @SerializedName("score") val score: Double?,
    @SerializedName("scientificName") val scientificName: String?,
    @SerializedName("commonNames") val commonNames: List<String>?,
    @SerializedName("family") val family: String?,
    @SerializedName("genus") val genus: String?
) : Parcelable
