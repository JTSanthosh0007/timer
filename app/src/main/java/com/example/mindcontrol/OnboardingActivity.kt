package com.example.mindcontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private var selectedUsageOption: Int = -1 // Stores estimated usage in minutes
    private var selectedOptionView: TextView? = null
    
    // Store top apps data
    private var topApps = mutableListOf<AppUsageInfo>()
    
    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val usageMinutes: Long
    )
    
    companion object {
        private const val REQUEST_OVERLAY = 100
        private const val REQUEST_USAGE = 101
        private const val REQUEST_ACCESSIBILITY = 102
        private const val AVERAGE_LIFESPAN_YEARS = 80
        private const val CURRENT_AGE_ASSUMPTION = 25
        private const val AWAKE_HOURS_PER_DAY = 16
        private const val SAVINGS_PERCENTAGE = 0.32f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if onboarding is already completed
        if (Utils.isOnboardingCompleted(this)) {
            navigateToMain()
            return
        }
        
        setContentView(R.layout.activity_onboarding)
        
        viewFlipper = findViewById(R.id.viewFlipper)
        
        setupWelcomeScreen()
        setupPermissionsScreen()
        setupSurveyScreen()
        setupCheckScreen()
        setupComparisonScreen()
        setupTopAppsScreen()
        setupTimeLossScreen()
        setupReclaimScreen()
    }
    
    private fun setupWelcomeScreen() {
        val btnStartNow = findViewById<Button>(R.id.btnStartNow)
        btnStartNow.setOnClickListener {
            viewFlipper.showNext()
        }
    }
    
    private fun setupPermissionsScreen() {
        val btnPermOverlay = findViewById<TextView>(R.id.btnPermOverlay)
        val btnPermUsage = findViewById<TextView>(R.id.btnPermUsage)
        val btnPermAccessibility = findViewById<TextView>(R.id.btnPermAccessibility)
        val btnPermContinue = findViewById<Button>(R.id.btnPermContinue)
        
        btnPermOverlay.setOnClickListener {
            requestOverlayPermission()
        }
        
        btnPermUsage.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        btnPermAccessibility.setOnClickListener {
            requestAccessibilityService()
        }
        
        btnPermContinue.setOnClickListener {
            viewFlipper.showNext()
        }
    }
    
    private fun setupSurveyScreen() {
        val options = listOf(
            findViewById<TextView>(R.id.optionLess1) to 30,    // Less than 1 hour = 30 min average
            findViewById<TextView>(R.id.option1to3) to 120,   // 1-3 hours = 2 hours average
            findViewById<TextView>(R.id.option3to5) to 240,   // 3-5 hours = 4 hours average
            findViewById<TextView>(R.id.option5to7) to 360,   // 5-7 hours = 6 hours average
            findViewById<TextView>(R.id.option7to9) to 480,   // 7-9 hours = 8 hours average
            findViewById<TextView>(R.id.optionMore9) to 600   // More than 9 hours = 10 hours average
        )
        
        val btnSurveyNext = findViewById<Button>(R.id.btnSurveyNext)
        
        for ((optionView, minutes) in options) {
            optionView.setOnClickListener {
                // Deselect previous
                selectedOptionView?.setBackgroundResource(R.drawable.bg_option_unselected)
                
                // Select new
                optionView.setBackgroundResource(R.drawable.bg_option_selected)
                selectedOptionView = optionView
                selectedUsageOption = minutes
                
                // Store the estimated usage
                Utils.setEstimatedUsage(this, minutes)
                
                // Enable Next button
                btnSurveyNext.isEnabled = true
                btnSurveyNext.setBackgroundResource(R.drawable.bg_button_white)
                btnSurveyNext.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }
        
        btnSurveyNext.setOnClickListener {
            if (selectedUsageOption > 0) {
                viewFlipper.showNext()
            }
        }
    }
    
    private fun setupCheckScreen() {
        val btnCheckNext = findViewById<Button>(R.id.btnCheckNext)
        btnCheckNext.setOnClickListener {
            // Fetch actual usage stats and go to comparison
            val actualUsage = getActualScreenTime()
            Utils.setActualUsage(this, actualUsage)
            
            // Load top apps for later screen
            loadTopApps()
            
            updateComparisonUI()
            viewFlipper.showNext()
        }
    }
    
    private fun setupComparisonScreen() {
        val btnCompareNext = findViewById<Button>(R.id.btnCompareNext)
        btnCompareNext.setOnClickListener {
            // Update top apps UI before showing
            updateTopAppsUI()
            viewFlipper.showNext()
        }
    }
    
    private fun setupTopAppsScreen() {
        val btnTopAppsNext = findViewById<Button>(R.id.btnTopAppsNext)
        btnTopAppsNext.setOnClickListener {
            // Calculate and update time loss before showing
            updateTimeLossUI()
            viewFlipper.showNext()
        }
    }
    
    private fun setupTimeLossScreen() {
        val btnTimeLossNext = findViewById<Button>(R.id.btnTimeLossNext)
        btnTimeLossNext.setOnClickListener {
            // Calculate and update reclaim time before showing
            updateReclaimUI()
            viewFlipper.showNext()
        }
    }
    
    private fun setupReclaimScreen() {
        val btnGetStarted = findViewById<Button>(R.id.btnGetStarted)
        btnGetStarted.setOnClickListener {
            // Mark onboarding as complete and go to main
            Utils.setOnboardingCompleted(this, true)
            navigateToMain()
        }
    }
    
    private fun loadTopApps() {
        if (!hasUsageStatsPermission()) {
            return
        }
        
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = packageManager
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        // Map to aggregate time for same package
        val addedPackages = mutableMapOf<String, Long>()
        
        for (stats in usageStats) {
            val pkg = stats.packageName
            val time = stats.totalTimeInForeground
            // Skip system UI or launchers if possible, but definitely skip very short usage
            if (time > 60000) { 
                addedPackages[pkg] = (addedPackages[pkg] ?: 0L) + time
            }
        }
        
        topApps.clear()
        
        // Convert map to list of AppUsageInfo
        for ((packageName, totalTime) in addedPackages) {
             try {
                // Filter out some common system apps if needed, but for now just getting info
                val appInfo = pm.getApplicationInfo(packageName, 0)
                
                // Hide system apps unless they are "updated system apps" (user visible typically)
                // or if the user actually launched them.
                // Simple check: if it has a launch intent, it's likely a user app
                if (pm.getLaunchIntentForPackage(packageName) == null) {
                    continue
                }
                
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val usageMinutes = totalTime / (1000 * 60)
                
                topApps.add(AppUsageInfo(packageName, appName, icon, usageMinutes))
            } catch (e: PackageManager.NameNotFoundException) {
                // Skip apps that can't be found
            }
        }
        
        // Sort by usage time descending
        topApps.sortByDescending { it.usageMinutes }
    }
    
    private fun updateTopAppsUI() {
        val actualMinutes = Utils.getActualUsage(this)
        
        // Update total time header
        val tvTotalTimeHeader = findViewById<TextView>(R.id.tvTotalTimeHeader)
        tvTotalTimeHeader.text = "In ${formatMinutes(actualMinutes)}..."
        
        // Update top 3 apps
        if (topApps.size >= 1) {
            val app1 = topApps[0]
            findViewById<ImageView>(R.id.ivApp1Icon).setImageDrawable(app1.icon)
            findViewById<TextView>(R.id.tvApp1Name).text = app1.appName
            findViewById<TextView>(R.id.tvApp1Time).text = formatMinutes(app1.usageMinutes.toInt())
            
            // Update progress bar width based on usage proportion
            val progress1 = findViewById<View>(R.id.progressApp1)
            val maxUsage = topApps[0].usageMinutes
            setProgressWidth(progress1, app1.usageMinutes, maxUsage)
        }
        
        if (topApps.size >= 2) {
            val app2 = topApps[1]
            findViewById<ImageView>(R.id.ivApp2Icon).setImageDrawable(app2.icon)
            findViewById<TextView>(R.id.tvApp2Name).text = app2.appName
            findViewById<TextView>(R.id.tvApp2Time).text = formatMinutes(app2.usageMinutes.toInt())
            
            val progress2 = findViewById<View>(R.id.progressApp2)
            setProgressWidth(progress2, app2.usageMinutes, topApps[0].usageMinutes)
        }
        
        if (topApps.size >= 3) {
            val app3 = topApps[2]
            findViewById<ImageView>(R.id.ivApp3Icon).setImageDrawable(app3.icon)
            findViewById<TextView>(R.id.tvApp3Name).text = app3.appName
            findViewById<TextView>(R.id.tvApp3Time).text = formatMinutes(app3.usageMinutes.toInt())
            
            val progress3 = findViewById<View>(R.id.progressApp3)
            setProgressWidth(progress3, app3.usageMinutes, topApps[0].usageMinutes)
        }
    }
    
    private fun setProgressWidth(view: View, usage: Long, maxUsage: Long) {
        val parentWidth = resources.displayMetrics.widthPixels - (48 * resources.displayMetrics.density).toInt()
        val proportion = if (maxUsage > 0) usage.toFloat() / maxUsage else 0f
        val width = (parentWidth * proportion * 0.8f).toInt().coerceAtLeast(40)
        
        view.layoutParams.width = width
        view.requestLayout()
    }
    
    private fun updateTimeLossUI() {
        val actualMinutes = Utils.getActualUsage(this)
        
        // Calculate years lost based on current usage projected over remaining lifespan
        // Assuming 16 hours awake per day
        val remainingYears = AVERAGE_LIFESPAN_YEARS - CURRENT_AGE_ASSUMPTION
        val hoursPerDay = actualMinutes / 60f
        val percentageOfDayOnPhone = hoursPerDay / AWAKE_HOURS_PER_DAY
        
        // Years lost = remaining years * percentage spent on phone
        val yearsLost = (remainingYears * percentageOfDayOnPhone).toInt()
        val daysLost = yearsLost * 365
        
        // Format with thousand separators
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
        
        val tvYearsLost = findViewById<TextView>(R.id.tvYearsLost)
        val tvDaysLost = findViewById<TextView>(R.id.tvDaysLost)
        
        tvYearsLost.text = "$yearsLost years"
        tvDaysLost.text = "${numberFormat.format(daysLost)} days"
    }
    
    private fun updateReclaimUI() {
        val actualMinutes = Utils.getActualUsage(this)
        
        // Calculate years that can be reclaimed (32% of lost time)
        val remainingYears = AVERAGE_LIFESPAN_YEARS - CURRENT_AGE_ASSUMPTION
        val hoursPerDay = actualMinutes / 60f
        val percentageOfDayOnPhone = hoursPerDay / AWAKE_HOURS_PER_DAY
        
        val yearsLost = (remainingYears * percentageOfDayOnPhone).toInt()
        val yearsReclaim = (yearsLost * SAVINGS_PERCENTAGE).toInt().coerceAtLeast(1)
        
        val tvYearsReclaim = findViewById<TextView>(R.id.tvYearsReclaim)
        tvYearsReclaim.text = "+$yearsReclaim years"
    }
    
    private fun updateComparisonUI() {
        val estimatedMinutes = Utils.getEstimatedUsage(this)
        val actualMinutes = Utils.getActualUsage(this)
        
        val tvEstimatedValue = findViewById<TextView>(R.id.tvEstimatedValue)
        val tvActualValue = findViewById<TextView>(R.id.tvActualValue)
        val tvCompareTitle = findViewById<TextView>(R.id.tvCompareTitle)
        val barEstimated = findViewById<View>(R.id.barEstimated)
        val barActual = findViewById<View>(R.id.barActual)
        
        // Format estimated time
        tvEstimatedValue.text = formatMinutes(estimatedMinutes)
        
        // Format actual time
        tvActualValue.text = formatMinutes(actualMinutes)
        
        // Update title based on comparison
        if (actualMinutes > estimatedMinutes) {
            tvCompareTitle.text = "Focus insights discovered"
        } else if (actualMinutes < estimatedMinutes) {
            tvCompareTitle.text = "Great! You use less\nthan you thought"
        } else {
            tvCompareTitle.text = "Your estimate was\nspot on!"
        }
        
        // Calculate and set bar heights (proportional)
        val maxMinutes = maxOf(estimatedMinutes, actualMinutes, 1)
        val maxHeight = 180 // Max height in dp
        
        val estimatedHeight = ((estimatedMinutes.toFloat() / maxMinutes) * maxHeight).toInt().coerceAtLeast(40)
        val actualHeight = ((actualMinutes.toFloat() / maxMinutes) * maxHeight).toInt().coerceAtLeast(40)
        
        val density = resources.displayMetrics.density
        barEstimated.layoutParams.height = (estimatedHeight * density).toInt()
        barActual.layoutParams.height = (actualHeight * density).toInt()
        
        barEstimated.requestLayout()
        barActual.requestLayout()
    }
    
    private fun formatMinutes(minutes: Int): String {
        return if (minutes < 60) {
            if (minutes <= 30) "≤ 1h" else "${minutes}m"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
    }
    
    private fun getActualScreenTime(): Int {
        if (!hasUsageStatsPermission()) {
            return 0
        }
        
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Get usage for last 24 hours
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // 24 hours ago
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        var totalTimeMs: Long = 0
        for (stats in usageStats) {
            totalTimeMs += stats.totalTimeInForeground
        }
        
        return (totalTimeMs / (1000 * 60)).toInt() // Convert to minutes
    }
    
    // Permission Requests
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY)
            } else {
                updatePermissionButton(R.id.btnPermOverlay, true)
            }
        }
    }
    
    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivityForResult(intent, REQUEST_USAGE)
        } else {
            updatePermissionButton(R.id.btnPermUsage, true)
        }
    }
    
    private fun requestAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, REQUEST_ACCESSIBILITY)
        } else {
            updatePermissionButton(R.id.btnPermAccessibility, true)
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.id.contains(packageName)) {
                return true
            }
        }
        return false
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Check permissions when returning from settings
        when (requestCode) {
            REQUEST_OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    updatePermissionButton(R.id.btnPermOverlay, true)
                }
            }
            REQUEST_USAGE -> {
                if (hasUsageStatsPermission()) {
                    updatePermissionButton(R.id.btnPermUsage, true)
                }
            }
            REQUEST_ACCESSIBILITY -> {
                if (isAccessibilityServiceEnabled()) {
                    updatePermissionButton(R.id.btnPermAccessibility, true)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update permission button states on resume
        updateAllPermissionButtons()
    }
    
    private fun updateAllPermissionButtons() {
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updatePermissionButton(R.id.btnPermOverlay, Settings.canDrawOverlays(this))
        }
        
        // Check usage stats permission
        updatePermissionButton(R.id.btnPermUsage, hasUsageStatsPermission())
        
        // Check accessibility service
        updatePermissionButton(R.id.btnPermAccessibility, isAccessibilityServiceEnabled())
    }
    
    private fun updatePermissionButton(buttonId: Int, isGranted: Boolean) {
        val button = findViewById<TextView>(buttonId)
        if (isGranted) {
            button.text = "✓"
            button.setBackgroundResource(R.drawable.bg_button_granted)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

