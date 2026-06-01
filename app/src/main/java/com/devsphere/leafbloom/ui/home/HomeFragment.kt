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
import androidx.core.widget.addTextChangedListener
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
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.beginFadeToggle
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.snapVisible
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

    // Survives view recreation (fragment instance is retained across nav forward/back).
    // Used to restore scroll position and skip the entrance choreography on return.
    private var savedScrollY: Int = 0
    private var hasPlayedEntrance: Boolean = false

    // Recent-scans search state. We keep the full list so the search can be re-applied
    // when either the data refreshes or the query changes.
    private var recentItems: List<HistoryItem> = emptyList()
    private var searchQuery: String = ""
    private var historyAdapter: HomeHistoryAdapter? = null

    // Edge-triggered debouncing for the search focus crossfade.
    private var lastFocusActive: Boolean? = null
    private var lastEmptyState: Boolean? = null

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
        postponeEnterTransition()

        // Lightweight layout setup — runs immediately
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        playEntrance()

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
                val adapter = HomeHistoryAdapter(
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
                historyAdapter = adapter
                rvHistory.layoutManager = LinearLayoutManager(requireContext())
                rvHistory.adapter = adapter
                rvHistory.layoutAnimation =
                    android.view.animation.AnimationUtils.loadLayoutAnimation(
                        requireContext(), R.anim.layout_animation_list
                    )

                setupSearch()

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
                        recentItems = items
                        renderRecent()
                    }
                }            }
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
        savedScrollY = _binding?.root?.scrollY ?: savedScrollY
        super.onDestroyView()
        _binding = null
        lastFocusActive = null
        lastEmptyState = null
    }

    // The recent-history RecyclerView populates async on return, so the page is shorter than
    // it was when the user left, and the NestedScrollView clamps any scrollTo() to the smaller
    // maxScrollY — landing the user "in the middle". Watch layout changes and re-apply scroll
    // once the content is tall enough to hold the saved position.
    private fun restoreScrollWhenReady() {
        val target = savedScrollY
        if (target <= 0 || _binding == null) return
        val sv = binding.root
        val tryRestore: () -> Boolean = {
            val child = sv.getChildAt(0)
            if (child != null) {
                val maxScroll = (child.height - sv.height).coerceAtLeast(0)
                if (maxScroll >= target) {
                    sv.scrollTo(0, target)
                    true
                } else false
            } else false
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int,
                oL: Int, oT: Int, oR: Int, oB: Int,
            ) {
                if (_binding == null || tryRestore()) sv.removeOnLayoutChangeListener(this)
            }
        }
        sv.addOnLayoutChangeListener(listener)
        // Attempt once immediately in case content is already tall enough.
        sv.post { if (_binding != null && tryRestore()) sv.removeOnLayoutChangeListener(listener) }
        // Safety: stop retrying after ~1.5s so the listener can't leak.
        sv.postDelayed({ sv.removeOnLayoutChangeListener(listener) }, 1500L)
    }

    private fun setupSearch() {
        val b = _binding ?: return
        b.etSearch.addTextChangedListener { editable ->
            val q = editable?.toString().orEmpty()
            searchQuery = q
            b.ivClearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            renderRecent()
        }
        b.ivClearSearch.setOnClickListener {
            b.etSearch.setText("")
        }
    }

    private fun applySearchFocus(active: Boolean) {
        val b = _binding ?: return
        val vis = if (active) View.GONE else View.VISIBLE
        listOf(
            b.chipsScroll,
            b.cardCheckPlant,
            b.tvAllFeatures,
            b.featuresRow1,
            b.featuresRow2,
            b.tvTips,
            b.tipsScroll,
        ).forEach { it.visibility = vis }
    }

    /** Applies the search query on top of [recentItems] and refreshes the section visibility. */
    private fun renderRecent() {
        val b = _binding ?: return
        val q = searchQuery.trim()
        val isSearching = q.isNotEmpty()
        val filtered = if (!isSearching) recentItems else recentItems.filter {
            it.plantName.contains(q, ignoreCase = true) ||
                it.status.contains(q, ignoreCase = true)
        }
        val hasResults = filtered.isNotEmpty()

        val focusTarget = isSearching && hasResults
        val emptyTarget = isSearching && !hasResults
        val focusChanged = lastFocusActive != focusTarget
        val emptyChanged = lastEmptyState != emptyTarget
        if (focusChanged || emptyChanged) {
            (b.root as ViewGroup).beginFadeToggle(excludeRecycler = b.rvHistory)
        }

        // Skip the recycler's layoutAnimation while a Fade is in flight — otherwise
        // the row items animate in concurrently and finish before the chrome fade-out
        // does, so the search results appear before the chrome is gone.
        historyAdapter?.submitList(filtered) {
            if (_binding != null && filtered.isNotEmpty() && !isSearching) {
                b.rvHistory.scheduleLayoutAnimation()
            }
        }

        lastFocusActive = focusTarget
        lastEmptyState = emptyTarget

        applySearchFocus(focusTarget || emptyTarget)

        if (emptyTarget) {
            b.tvNoResults.text = getString(R.string.no_results_for, q)
            b.tvNoResults.visibility = View.VISIBLE
            b.tvHistory.visibility = View.GONE
            b.cardHistory.visibility = View.GONE
        } else {
            b.tvNoResults.visibility = View.GONE
            val sectionVisible = recentItems.isNotEmpty()
            b.tvHistory.visibility = if (sectionVisible) View.VISIBLE else View.GONE
            b.cardHistory.visibility = if (sectionVisible) View.VISIBLE else View.GONE
        }
    }

    private fun playEntrance() {
        val sections = listOf(
            binding.searchContainer,
            binding.cardWeather,
            binding.chipsScroll,
            binding.cardCheckPlant,
            binding.tvAllFeatures,
            binding.featuresRow1,
            binding.featuresRow2,
            binding.tvHistory,
            binding.cardHistory,
            binding.tvTips,
        )

        // Tactile press feedback on the main interactive cards.
        listOf(binding.cardWeather, binding.cardCheckPlant, binding.searchContainer)
            .forEach { it.bounceOnPress() }

        // Return path (back-nav into home): skip choreography, snap content visible,
        // and restore scroll once layout settles. Otherwise the user is at the bottom,
        // leaves, returns, and sees the page snap to the middle as primeFadeUp +
        // scroll-restore race each other.
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            sections.forEach { it.snapVisible() }
            restoreScrollWhenReady()
            startPostponedEnterTransition()
            return
        }

        // Prime immediately so the first frame is invisible. Re-prime inside the
        // pre-draw listener to defend against anything (insets, adapter setup,
        // chip relayout) resetting alpha/scale between now and the entrance.
        // Home cards are larger than Profile/DiseaseLibrary, so subtle motion
        // (16-24dp) doesn't read — use a bigger drop + more aggressive scale.
        sections.forEach { it.primeScaleFadeUp(translationDp = 64f, startScale = 0.88f) }

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            var delay = 0L
            sections.forEach { v ->
                v.primeScaleFadeUp(translationDp = 64f, startScale = 0.88f)
                v.entranceScaleFadeUp(delay = delay, duration = 700L)
                delay += 100L
            }
            hasPlayedEntrance = true
        }
    }
}