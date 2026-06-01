package com.devsphere.leafbloom.ui.main

import android.os.Bundle
import android.os.StrictMode
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.devsphere.leafbloom.BuildConfig
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.prefs.UserPrefs
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        lifecycleScope.launch(Dispatchers.IO) { UserPrefs.getInstance(this@MainActivity) }

        // ✅ Get NavController
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // -------------------------------------------------------
        // Programmatically inflate nav graph (no app:navGraph in XML)
        // so we can set the correct start destination BEFORE any
        // fragment is created.
        // -------------------------------------------------------
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        val isFirstRun = UserPrefs.getInstance(this).isFirstRun
        navGraph.setStartDestination(
            if (isFirstRun) R.id.walkthroughFragment else R.id.homeFragment
        )
        navController.graph = navGraph

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        val indicator = findViewById<View>(R.id.bottom_nav_indicator)
        val fab = findViewById<View>(R.id.fab_scan)

        // Edge-to-edge: do NOT push content up from bottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, 0)
            insets
        }

        // 🔗 BottomNav ↔ NavGraph (THIS is what you wanted)
        NavigationUI.setupWithNavController(bottomNav, navController)

        // Disable selecting the spacer
        bottomNav.menu.findItem(R.id.center_spacer)?.apply {
            isEnabled = false
            isCheckable = false
        }

        // Move indicator. Run inline when the target view is already laid out
        // (the common case after first frame) so the move is instant; only
        // defer to post when width is still 0.
        fun moveIndicator(itemId: Int) {
            val itemView = bottomNav.findViewById<View>(itemId) ?: return
            val apply = {
                indicator.animate().cancel()
                indicator.translationX =
                    itemView.left + itemView.width / 2f - indicator.width / 2f
            }
            if (itemView.width > 0 && indicator.width > 0) apply() else indicator.post(apply)
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.center_spacer) return@setOnItemSelectedListener false
            // Move indicator immediately on tap, before the (slower) fragment transition.
            moveIndicator(item.itemId)
            NavigationUI.onNavDestinationSelected(item, navController)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            moveIndicator(destination.id)

            val isFullscreen = destination.id in setOf(
                R.id.homeFragment,
                R.id.diseaseLibraryFragment,
                R.id.historyFragment,
                R.id.profileFragment
            )
            val container = findViewById<View>(R.id.bottom_nav_container)
            if (isFullscreen) {
                // Wait for the new destination fragment's own view to be created and
                // drawn before revealing the bottom nav. Listening on nav_host_fragment
                // fires too early — the host is always laid out, so its pre-draw runs
                // before the incoming fragment's view is even attached. The destination
                // fragment may also be postponing its own enter transition.
                showNavWhenDestinationDrawn(navHostFragment, container)
            } else {
                container.visibility = View.GONE
            }
        }

        bottomNav.doOnPreDraw {
            if (bottomNav.selectedItemId == 0) {
                bottomNav.selectedItemId = R.id.homeFragment
            }
            moveIndicator(bottomNav.selectedItemId)
        }

        // FAB → Scan destination (use nav_graph)
        fab.setOnClickListener {
            navController.navigate(R.id.scannerFragment)
        }
    }

    /**
     * Reveal [container] only once the new destination fragment's own view has been
     * created and gone through one draw pass. Defers correctly when the fragment
     * calls [androidx.fragment.app.Fragment.postponeEnterTransition].
     */
    private fun showNavWhenDestinationDrawn(
        navHostFragment: NavHostFragment,
        container: View,
    ) {
        // Wait for the destination fragment to reach RESUMED — that lifecycle
        // step is gated by postponeEnterTransition, so it only fires after the
        // fragment's view is actually drawn and the enter transition has run.
        // Listening earlier (onFragmentViewCreated / pre-draw on nav_host) reveals
        // the bar before the previous screen has finished leaving.
        val fm = navHostFragment.childFragmentManager
        fm.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(
                    fm: androidx.fragment.app.FragmentManager,
                    f: androidx.fragment.app.Fragment,
                ) {
                    fm.unregisterFragmentLifecycleCallbacks(this)
                    container.visibility = View.VISIBLE
                }
            },
            false,
        )
    }
}

