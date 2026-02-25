package com.devsphere.leafbloom.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.databinding.FragmentHomeBinding
import com.devsphere.leafbloom.ui.adapter.HistoryAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.dialog.RationaleDialog
import com.devsphere.leafbloom.util.PermissionManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.material.chip.Chip
import java.util.Locale

class HomeFragment : BaseFragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Location Permission Launcher
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val isGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (isGranted) {
                checkLocationSettings()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.location_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
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
            Toast.makeText(
                requireContext(), getString(R.string.location_services_required), Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Notification Permission Launcher
    private val requestNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Toast.makeText(requireContext(), "Notification permission granted!", Toast.LENGTH_SHORT).show()
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

        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

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
                if (PermissionManager.hasPermission(
                        requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                ) {
                    checkLocationSettings()
                } else {
                    checkLocationPermission()
                }
            }

            // Reminders Card
            featuresRow2.getChildAt(1)?.setOnClickListener {
                checkNotificationPermission()
            }

            // Feature: Identify -> Navigate to Scanner with mode=IDENTIFY
            // featuresRow1 child 1 is Identify Card
            featuresRow1.getChildAt(1)?.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("scan_mode", "IDENTIFY")
                }
                findNavController().navigate(R.id.action_homeFragment_to_scannerFragment, bundle)
            }

            // Initialize History RecyclerView
            val historyItems = listOf(
                HistoryItem(
                    "Rose", "Healthy", 37, "25 November, 12:00 am", R.drawable.history_item
                ), HistoryItem(
                    "Lily", "Healthy", 37, "25 November, 12:00 am", R.drawable.history_item
                ), HistoryItem(
                    "Apple", "Healthy", 37, "25 November, 12:00 am", R.drawable.history_item
                )
            )
            val historyAdapter = HistoryAdapter(historyItems) {
                findNavController().navigate(R.id.action_homeFragment_to_historyDetailsFragment)
            }
            rvHistory.layoutManager = LinearLayoutManager(requireContext())
            rvHistory.adapter = historyAdapter
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
            if (location != null) {
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
                    binding.tvLocationValue.text = "Unknown Location"
                }
            } else {
                binding.tvLocationValue.text = "Location unavailable"
            }
        }
    }

    private fun updateLocationUI(address: android.location.Address) {
        val city = address.locality ?: address.subAdminArea ?: "Unknown City"
        val country = address.countryName ?: "Unknown Country"
        val text = "$city, $country"

        requireActivity().runOnUiThread {
            binding.tvLocationValue.text = text
            binding.tvWeatherLocation.text = text
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}