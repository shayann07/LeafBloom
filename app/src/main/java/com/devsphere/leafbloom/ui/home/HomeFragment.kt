package com.devsphere.leafbloom.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.model.PestInfo
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.data.repository.WeatherError
import com.devsphere.leafbloom.data.source.local.db.LeafBloomDatabase
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import com.devsphere.leafbloom.databinding.FragmentHomeBinding
import com.devsphere.leafbloom.ui.adapter.HomeHistoryAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.dialog.RationaleDialog
import com.devsphere.leafbloom.util.DateUtils
import com.devsphere.leafbloom.util.PermissionManager
import com.devsphere.leafbloom.util.WeatherCodeMapper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(requireActivity().application)
    }

    private var lastKnownLat: Double? = null
    private var lastKnownLon: Double? = null

    // Location Permission Launcher
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (isGranted) {
                checkLocationSettings()
            } else {
                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                    requireView(),
                    getString(R.string.location_permission_denied),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                    com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                )
                // Proceed to ask for notifications even if location denied
                checkNotificationPermission()
            }
        }

    // Location Settings Resolution Launcher (GPS enable dialog)
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            fetchLocation()
        } else {
            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                requireView(),
                getString(R.string.location_services_required),
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
            )
        }
    }

    // Notification Permission Launcher
    private val requestNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(requireView(), "Notification permission granted!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lightweight layout setup — runs immediately
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.weather.collect { renderWeather(it) }
            }
        }

        // Defer heavy work until after the first frame draws
        // so back-navigation transitions render smoothly
        view.post {
            if (!isAdded) return@post

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

            // Auto-start permission flow
            checkLocationPermission()

            binding.apply {
                // ... (Existing chip logic) ...
                chipGroup.children.forEach { view ->
                    (view as? Chip)?.let { chip ->
                        chip.tag = chip.text
                        chip.text = if (chip.isChecked) chip.tag.toString() else ""
                    }
                }

                chipGroup.setOnCheckedStateChangeListener { group, _ ->
                    TransitionManager.beginDelayedTransition(chipsScroll)
                    group.children.forEach { view ->
                        (view as? Chip)?.let { chip ->
                            chip.text = if (chip.isChecked) chip.tag.toString() else ""
                        }
                    }
                }

                // Weather Card -> Click to refresh location manually
                cardWeather.setOnClickListener {
                    val lat = lastKnownLat
                    val lon = lastKnownLon
                    if (lat != null && lon != null) {
                        homeViewModel.onLocation(lat, lon, force = true)
                    } else if (PermissionManager.hasPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    ) {
                        checkLocationSettings()
                    } else {
                        checkLocationPermission()
                    }
                }

                // Feature: Pest ID -> Navigate to Scanner with mode=PEST
                featuresRow2.getChildAt(0)?.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("scan_mode", "PEST")
                    }
                    findNavController().navigate(R.id.action_homeFragment_to_scannerFragment, bundle)
                }

                // Feature: Ripeness Check -> Navigate to Scanner with mode=RIPENESS
                featuresRow2.getChildAt(1)?.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("scan_mode", "RIPENESS")
                    }
                    findNavController().navigate(R.id.action_homeFragment_to_scannerFragment, bundle)
                }

                // Feature: Diagnose -> Navigate to Scanner with mode=DIAGNOSE (only for Tomato)
                val navigateToDiagnose = View.OnClickListener {
                    val checkedChipId = chipGroup.checkedChipId
                    if (checkedChipId == R.id.chipTomato) {
                        val bundle = Bundle().apply {
                            putString("scan_mode", "DIAGNOSE")
                        }
                        findNavController().navigate(R.id.action_homeFragment_to_scannerFragment, bundle)
                    } else {
                        val selectedPlant = chipGroup.findViewById<com.google.android.material.chip.Chip>(checkedChipId)?.tag?.toString() ?: "this plant"
                        com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                            binding.root,
                            "$selectedPlant diagnosis is coming soon! Our AI is currently studying it.",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                            com.devsphere.leafbloom.util.SnackbarUtils.Type.INFO
                        )
                    }
                }
                btnDiagnose.setOnClickListener(navigateToDiagnose)
                cardCheckPlant.setOnClickListener(navigateToDiagnose)
                featuresRow1.getChildAt(0)?.setOnClickListener(navigateToDiagnose)

                // Feature: Identify -> Navigate to Scanner with mode=IDENTIFY
                // featuresRow1 child 1 is Identify Card
                featuresRow1.getChildAt(1)?.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("scan_mode", "IDENTIFY")
                    }
                    findNavController().navigate(R.id.action_homeFragment_to_scannerFragment, bundle)
                }

                // Initialize History RecyclerView with live Room data
                val db = LeafBloomDatabase.getInstance(requireContext())
                val historyRepo = ScanHistoryRepository(db.scanHistoryDao())
                val historyAdapter = HomeHistoryAdapter(
                    onItemClick = { item ->
                        when (item.scanType) {
                            "PEST" -> {
                                val bundle = Bundle().apply {
                                    putString("image_uri", item.imagePath)
                                    putString("predicted_class_name", item.plantName)
                                    putFloat("confidence", item.confidence / 100f)
                                }
                                findNavController().navigate(R.id.action_homeFragment_to_pestResultFragment, bundle)
                            }
                            "IDENTIFY" -> {
                                val bundle = Bundle().apply {
                                    putString("image_uri", item.imagePath)
                                    putLong("scanId", item.id)
                                }
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val entity = historyRepo.getById(item.id)
                                    val response = entity?.identifyResponseJson?.let {
                                        Gson().fromJson(it, IdentifyResponse::class.java)
                                    }
                                    if (response != null) {
                                        val navBundle = Bundle().apply {
                                            putString("image_uri", item.imagePath)
                                            putParcelable("identify_response", response)
                                        }
                                        findNavController().navigate(R.id.action_homeFragment_to_identifyResultFragment, navBundle)
                                    }
                                }
                            }
                            else -> {
                                val bundle = Bundle().apply {
                                    putLong("scanId", item.id)
                                }
                                findNavController().navigate(R.id.action_homeFragment_to_historyDetailsFragment, bundle)
                            }
                        }
                    }
                )
                rvHistory.layoutManager = LinearLayoutManager(requireContext())
                rvHistory.adapter = historyAdapter

                // Observe recent 3 history items
                viewLifecycleOwner.lifecycleScope.launch {
                    historyRepo.observeRecent(3).collectLatest { entities ->
                        val items = entities.map { entity ->
                            val isHealthy = entity.predictedClass.equals("Healthy", ignoreCase = true)
                            var displayPlantName = entity.predictedClass

                            val status = when (entity.scanType) {
                                "PEST" -> {
                                    val pestInfo = PestInfo.get(entity.predictedClass)
                                    try { getString(pestInfo.threatLevelRes) } catch (_: Exception) { "Unknown" }
                                }
                                "IDENTIFY" -> {
                                    entity.identifyResponseJson?.let {
                                        try {
                                            val response = Gson().fromJson(it, IdentifyResponse::class.java)
                                            val bestMatch = response.data?.results?.firstOrNull()
                                            
                                            val commonName = bestMatch?.commonNames?.firstOrNull()
                                            if (!commonName.isNullOrBlank()) {
                                                displayPlantName = commonName.replaceFirstChar { char ->
                                                    if (char.isLowerCase()) char.titlecase(java.util.Locale.ROOT) else char.toString()
                                                }
                                            }
                                            "Identified"
                                        } catch (_: Exception) { "Unknown" }
                                    } ?: "Unknown"
                                }
                                else -> if (isHealthy) "Healthy" else "Infected"
                            }

                            HistoryItem(
                                id = entity.id,
                                plantName = displayPlantName,
                                status = status,
                                confidence = (entity.confidence * 100).toInt(),
                                date = DateUtils.getSmartDate(entity.timestampMs),
                                imagePath = entity.imagePath,
                                isHealthy = isHealthy,
                                scanType = entity.scanType
                            )
                        }
                        historyAdapter.submitList(items)

                        // Hide entire history section when empty
                        if (items.isEmpty()) {
                            tvHistory.visibility = View.GONE
                            cardHistory.visibility = View.GONE
                        } else {
                            tvHistory.visibility = View.VISIBLE
                            cardHistory.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkLocationPermission() {
        if (PermissionManager.hasPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            checkLocationSettings()
            // Check Notification permission after location is handled (or already granted)
            checkNotificationPermission()
        } else if (PermissionManager.shouldShowRationale(
                requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            RationaleDialog(
                titleStr = getString(R.string.enable_location_title),
                descriptionStr = getString(R.string.enable_location_desc),
                iconResId = R.drawable.location_icon,
                onPositive = {
                    requestLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                onNegative = { checkNotificationPermission() } // Continue flow even if denied
            ).show(childFragmentManager, RationaleDialog.TAG)
        } else {
            requestLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkLocationSettings() {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(requireContext())
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize location requests here.
            fetchLocation()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: Exception) {
                    // Ignore the error.
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (_binding == null) return@addOnSuccessListener
            if (location != null) {
                lastKnownLat = location.latitude
                lastKnownLon = location.longitude
                homeViewModel.onLocation(location.latitude, location.longitude)
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    // Geocoder might be blocking, usually better on background thread, 
                    // but usually fast enough for simple UI updates on recent Android versions or cached.
                    // For production, use coroutines. Kept simple here per request structure.

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            com.devsphere.leafbloom.util.LocationUtils.getFromLocationAndroid13(
                                geocoder, location.latitude, location.longitude
                            ) { address ->
                                if (address != null) updateLocationUI(address)
                            }
                        } catch (e: Exception) {
                            // Fallback or ignore
                            e.printStackTrace()
                        }
                    } else {
                        @Suppress("DEPRECATION") val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            updateLocationUI(addresses[0])
                        }
                    }
                } catch (e: Exception) {
                    // e.printStackTrace()
                    _binding?.tvLocationValue?.text = "Unknown Location"
                }
            } else {
                _binding?.tvLocationValue?.text = "Location unavailable"
            }
        }
    }

    private fun updateLocationUI(address: android.location.Address) {
        val city = address.locality ?: address.subAdminArea ?: "Unknown City"
        val country = address.countryName ?: "Unknown Country"
        val text = "$city, $country"

        requireActivity().runOnUiThread {
            _binding?.tvLocationValue?.text = text
            _binding?.tvWeatherLocation?.text = text
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (!PermissionManager.hasPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            if (PermissionManager.shouldShowRationale(
                    requireActivity(), Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                RationaleDialog(
                    titleStr = getString(R.string.enable_notifications_title),
                    descriptionStr = getString(R.string.enable_notifications_desc),
                    iconResId = R.drawable.tree_icon,
                    onPositive = { requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onNegative = {}).show(childFragmentManager, RationaleDialog.TAG)
            } else {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun renderWeather(state: WeatherUiState) {
        val b = _binding ?: return
        when (state) {
            is WeatherUiState.Empty -> {
                b.tvTemp.text = getString(R.string.weather_temp_placeholder)
                b.ivWeatherIcon.setImageResource(R.drawable.weather_cloud)
                b.pbWeather.visibility = View.GONE
            }
            is WeatherUiState.Data -> {
                val current = state.weather.current
                val isDay = current.isDay == 1
                val visual = WeatherCodeMapper.visualFor(current.weatherCode, isDay)
                b.tvTemp.text = getString(
                    R.string.weather_temp_format,
                    current.temperature2m.roundToInt()
                )
                b.ivWeatherIcon.setImageResource(visual.iconRes)
                b.pbWeather.visibility = if (state.isRefreshing) View.VISIBLE else View.GONE

                state.lastError?.let { showWeatherError(it, hasCachedData = true) }
            }
        }
    }

    private fun showWeatherError(error: WeatherError, hasCachedData: Boolean) {
        val msg = when (error) {
            is WeatherError.NoNetwork ->
                if (hasCachedData) getString(R.string.weather_offline_cached)
                else getString(R.string.weather_error_generic)
            is WeatherError.RateLimited -> getString(R.string.weather_error_rate_limited)
            else -> getString(R.string.weather_error_generic)
        }
        com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
            requireView(),
            msg,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
            com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}