package com.devsphere.leafbloom.util

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi

object LocationUtils {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getFromLocationAndroid13(
        geocoder: Geocoder, 
        lat: Double, 
        lng: Double, 
        callback: (Address?) -> Unit
    ) {
        geocoder.getFromLocation(lat, lng, 1) { addresses ->
            if (addresses.isNotEmpty()) {
                callback(addresses[0])
            } else {
                callback(null)
            }
        }
    }
}
