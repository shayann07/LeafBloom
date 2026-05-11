package com.devsphere.leafbloom.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devsphere.leafbloom.R
import androidx.core.view.doOnPreDraw
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.devsphere.leafbloom.prefs.UserPrefs
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        // Move your EXISTING indicator drawable
        fun moveIndicator(itemId: Int) {
            val itemView = bottomNav.findViewById<View>(itemId) ?: return
            indicator.post {
                indicator.translationX = itemView.left + itemView.width / 2f - indicator.width / 2f
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.center_spacer) return@setOnItemSelectedListener false
            val handled = NavigationUI.onNavDestinationSelected(item, navController)
            if (handled) moveIndicator(item.itemId)
            handled
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            moveIndicator(destination.id)

            val isFullscreen = destination.id == R.id.homeFragment
            val visibility = if (isFullscreen) View.VISIBLE else View.GONE
            val container = findViewById<View>(R.id.bottom_nav_container)

            // Post to next frame so the nav bar and fragment content appear together
            container.post {
                container.visibility = visibility
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
}

