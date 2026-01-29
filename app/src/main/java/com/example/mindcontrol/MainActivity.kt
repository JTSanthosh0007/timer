package com.example.mindcontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.Collections
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnStart: Button
    private lateinit var tvSelectedApps: TextView
    private lateinit var setupLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var lockedLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var npHour: NumberPicker
    private lateinit var npMinute: NumberPicker
    private lateinit var progressTimer: CircularProgressIndicator

    private lateinit var endedLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var tvEndTimeValue: TextView
    private var alarmPlayer: android.media.MediaPlayer? = null
    private var isTimerJustFinished = false
    
    private val allApps = mutableListOf<AppInfo>()
    private val displayApps = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter
    

    
    // Receiver for timer updates
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Utils.ACTION_TIMER_TICK -> {
                    val millisUntilFinished = intent.getLongExtra(Utils.EXTRA_TIME_REMAINING, 0)
                    tvTimer.text = Utils.formatTime(millisUntilFinished)
                    
                    val totalDuration = Utils.getTotalDuration(this@MainActivity)
                    if (totalDuration > 0) {
                        val progress = ((millisUntilFinished.toFloat() / totalDuration.toFloat()) * 1000).toInt()
                        progressTimer.progress = progress
                    }
                }
                Utils.ACTION_TIMER_FINISHED -> {
                    // Record History
                    val totalDuration = Utils.getTotalDuration(this@MainActivity)
                    if (totalDuration > 0) {
                        Utils.addToTotalHistory(this@MainActivity, totalDuration)
                    }
                    
                    isTimerJustFinished = true
                    playAlarm()
                    updateUIState()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        // Bind Views
        rvApps = findViewById(R.id.rvApps)
        etSearch = findViewById(R.id.etSearch)
        btnStart = findViewById(R.id.btnStart)
        tvSelectedApps = findViewById(R.id.tvSelectedApps)
        setupLayout = findViewById(R.id.setupLayout)
        lockedLayout = findViewById(R.id.lockedLayout) // Note: This is now a ConstraintLayout in XML
        endedLayout = findViewById(R.id.endedLayout)
        tvTimer = findViewById(R.id.tvTimer)
        progressTimer = findViewById(R.id.progressTimer)
        tvEndTimeValue = findViewById(R.id.tvEndTimeValue)
        
        // Dock Listeners
        findViewById<View>(R.id.dockPhone)?.setOnClickListener {
             val intent = Intent(Intent.ACTION_DIAL)
             startActivity(intent)
        }
        
        findViewById<View>(R.id.dockMessage)?.setOnClickListener {
             val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(this)
             if (defaultSms != null) {
                 val intent = packageManager.getLaunchIntentForPackage(defaultSms)
                 if (intent != null) startActivity(intent)
             } else {
                 val intent = Intent(Intent.ACTION_MAIN)
                 intent.addCategory(Intent.CATEGORY_APP_MESSAGING)
                 startActivity(intent)
             }
        }
        
        findViewById<View>(R.id.dockApps)?.setOnClickListener {
             showAllowedAppsDialog()
        }

        // Ended Screen Listeners
        findViewById<Button>(R.id.btnCheck)?.setOnClickListener {
            stopAlarm()
            isTimerJustFinished = false
            updateUIState()
        }
        
        findViewById<Button>(R.id.btnScheduleNext)?.setOnClickListener {
            stopAlarm()
            isTimerJustFinished = false
            updateUIState()
        }
        
        npHour = findViewById(R.id.npHour)
        npMinute = findViewById(R.id.npMinute)

        // Update Total Time immediately - View Removed in new UI
        // val tvTotalLockTime = findViewById<TextView>(R.id.tvTotalLockTime)
        // tvTotalLockTime.text = Utils.formatHistoryTime(Utils.getTotalHistory(this))
        
        // Tab Linking
        val btnTabPhone = findViewById<TextView>(R.id.btnTabPhone)
        val btnTabApp = findViewById<TextView>(R.id.btnTabApp)
        val btnTabSchedule = findViewById<TextView>(R.id.btnTabSchedule)
        val timerSection = findViewById<LinearLayout>(R.id.timerSection)
        val appListContainer = findViewById<LinearLayout>(R.id.appListContainer)
        val scheduleHistory = findViewById<TextView>(R.id.tvScheduleHistory)

        btnTabPhone.setOnClickListener {
            // Select Phone
            btnTabPhone.setBackgroundResource(R.drawable.bg_segment_selected)
            btnTabPhone.setTextColor(getColor(R.color.white))
            
            btnTabApp.setBackgroundResource(0)
            btnTabApp.setTextColor(getColor(R.color.gray_text))
            
            timerSection.visibility = View.VISIBLE
            appListContainer.visibility = View.GONE
        }
        
        btnTabApp.setOnClickListener {
            // Select App
            btnTabApp.setBackgroundResource(R.drawable.bg_segment_selected)
            btnTabApp.setTextColor(getColor(R.color.white))
            
            btnTabPhone.setBackgroundResource(0)
            btnTabPhone.setTextColor(getColor(R.color.gray_text))
            
            timerSection.visibility = View.GONE
            appListContainer.visibility = View.VISIBLE
            
            // Make sure apps are loaded
            if (allApps.isEmpty()) {
                loadApps()
            }
            
            // Update the selected apps summary
            updateSelectedAppsSummary()
        }
        
        btnTabSchedule.setOnClickListener {
            // Show last lock duration if available
            val prefs = getSharedPreferences("lock_history", Context.MODE_PRIVATE)
            val last = prefs.getInt("last_duration", 0)
            if (last > 0) {
                val hours = last / 60
                val minutes = last % 60
                scheduleHistory.text = "Last: ${hours}h ${minutes}m (Tap to repeat)"
                scheduleHistory.visibility = View.VISIBLE
                scheduleHistory.setOnClickListener {
                    performLock(hours, minutes)
                }
            } else {
                scheduleHistory.text = "No history yet"
                scheduleHistory.visibility = View.VISIBLE
            }
            timerSection.visibility = View.GONE
            appListContainer.visibility = View.GONE
        }
        
        // Setup Pickers (White Text Hack)
        // ... (Keep existing Logic)
        npHour.minValue = 0
        npHour.maxValue = 23
        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.value = 1 // Default 1 min
        
        // Formatter for 00 style
        val formatter = NumberPicker.Formatter { i -> String.format("%02d", i) }
        npHour.setFormatter(formatter)
        npMinute.setFormatter(formatter)

        // Add Haptic/Click Feedback with explicit Vibration
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val soundListener = NumberPicker.OnValueChangeListener { picker, _, _ ->
             // Explicit Vibration for clearer feedback
             if (Build.VERSION.SDK_INT >= 26) {
                 // 80ms duration, 255 (MAX) amplitude for much stronger feedback
                 vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, 255))
             } else {
                 vibrator.vibrate(80)
             }
             
             // Sound: CLICK is standard and widely supported
             picker.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        }
        npHour.setOnValueChangedListener(soundListener)
        npMinute.setOnValueChangedListener(soundListener)

        rvApps.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(displayApps) {
            updateSelectedAppsSummary()
        }
        rvApps.adapter = adapter

        btnStart.setOnClickListener { startFocusMode() }
        updateSelectedAppsSummary()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
        })
        
        // Keep app list visible when search gets focus
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val appListContainer = findViewById<LinearLayout>(R.id.appListContainer)
                val timerSection = findViewById<LinearLayout>(R.id.timerSection)
                appListContainer.visibility = View.VISIBLE
                timerSection.visibility = View.GONE
                
                // Also update tab visuals
                val btnTabApp = findViewById<TextView>(R.id.btnTabApp)
                val btnTabPhone = findViewById<TextView>(R.id.btnTabPhone)
                btnTabApp.setBackgroundResource(R.drawable.bg_segment_selected)
                btnTabApp.setTextColor(getColor(R.color.white))
                btnTabPhone.setBackgroundResource(0)
                btnTabPhone.setTextColor(getColor(R.color.gray_text))
            }
        }
        
        // Initial check
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (Utils.isLocked(this)) {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                startLockTask()
            }
            updateUIState()
        } else {
            loadApps()
        }

        // Initialize AdMob
        com.google.android.gms.ads.MobileAds.initialize(this) {}
        
        // Load Native Ad
        val adLoader = com.google.android.gms.ads.AdLoader.Builder(this, "ca-app-pub-4060024795112786/6134252090")
            .forNativeAd { nativeAd ->
                val adFrame = findViewById<android.widget.FrameLayout>(R.id.ad_frame)
                
                // Inflate Native Ad View
                val adView = layoutInflater.inflate(R.layout.ad_native, null) as com.google.android.gms.ads.nativead.NativeAdView
                
                // Populate Assets
                adView.headlineView = adView.findViewById(R.id.ad_headline)
                adView.bodyView = adView.findViewById(R.id.ad_body)
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
                adView.iconView = adView.findViewById(R.id.ad_app_icon)
                adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
                
                // Set Text
                (adView.headlineView as TextView).text = nativeAd.headline
                (adView.bodyView as TextView).text = nativeAd.body
                (adView.callToActionView as Button).text = nativeAd.callToAction
                (adView.advertiserView as TextView).text = nativeAd.advertiser
                
                // Set Icon
                val icon = nativeAd.icon
                if (icon != null) {
                    (adView.iconView as android.widget.ImageView).setImageDrawable(icon.drawable)
                    adView.iconView?.visibility = View.VISIBLE
                } else {
                    adView.iconView?.visibility = View.GONE
                }
                
                // Register Views
                adView.setNativeAd(nativeAd)
                
                // Add to Frame
                adFrame.removeAllViews()
                adFrame.addView(adView)
            }
            .build()
            
        adLoader.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    override fun onBackPressed() {
        if (Utils.isLocked(this)) {
            // Block back button
            return
        }
        super.onBackPressed()
    }

    // Reliable UI Update Loop
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val uiRunnable = object : Runnable {
        override fun run() {
            if (Utils.isLocked(this@MainActivity)) {
                val endTime = Utils.getEndTime(this@MainActivity)
                val totalDuration = Utils.getTotalDuration(this@MainActivity)
                val remaining = endTime - System.currentTimeMillis()
                
                if (remaining > 0) {
                    tvTimer.text = Utils.formatTime(remaining)
                    if (totalDuration > 0) {
                         val progress = ((remaining.toFloat() / totalDuration.toFloat()) * 1000).toInt()
                         progressTimer.progress = progress
                    }
                } else {
                    tvTimer.text = "00:00:00"
                    progressTimer.progress = 0
                    // Don't call updateUIState here repeatedly or it might flicker the ended screen if not careful
                    // But if we are locked, we are locked.
                    // If time is up, the service triggers FINISHED.
                }
                
                // Update frequently for smoothness
                uiHandler.postDelayed(this, 500)
            } else {
                // If not locked, stop updates
                uiHandler.removeCallbacks(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        
        if (intent.getBooleanExtra("show_ended_screen", false)) {
            intent.removeExtra("show_ended_screen")
            isTimerJustFinished = true
            playAlarm()
        }
        
        updateUIState()
        
        // Start UI updates
        uiHandler.post(uiRunnable)
        
        // Register receiver for UI updates (keep for Finish event)
        val filter = IntentFilter()
        filter.addAction(Utils.ACTION_TIMER_TICK)
        filter.addAction(Utils.ACTION_TIMER_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRunnable)
        try {
             unregisterReceiver(timerReceiver)
        } catch (e: Exception) {
             // Receiver might not be registered
        }
    }

    private fun updateUIState() {
        if (isTimerJustFinished) {
            setupLayout.visibility = View.GONE
            lockedLayout.visibility = View.GONE
            endedLayout.visibility = View.VISIBLE
            return
        }
        
        endedLayout.visibility = View.GONE

        if (Utils.isLocked(this)) {
            setupLayout.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            
            val endTime = Utils.getEndTime(this)
            val totalDuration = Utils.getTotalDuration(this)
            val remaining = endTime - System.currentTimeMillis()
            
            // Format End Time (e.g. Jan 28, 12 pm)
            val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            tvEndTimeValue.text = sdf.format(java.util.Date(endTime))
            
            if (remaining > 0) {
                 tvTimer.text = Utils.formatTime(remaining)
                 if (totalDuration > 0) {
                     val progress = ((remaining.toFloat() / totalDuration.toFloat()) * 1000).toInt()
                     progressTimer.progress = progress
                 }
            } else {
                 tvTimer.text = "00:00:00"
                 progressTimer.progress = 0
            }
            
        } else {
            setupLayout.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
        }
        // If timer just finished or unlocked, exit lock task mode
        if (!Utils.isLocked(this)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try { stopLockTask() } catch (_: Exception) {}
            }
        }
    }

    private fun showAllowedAppsDialog() {
        val allowedPackages = Utils.getAllowedApps(this)
        val allowedAppsList = mutableListOf<AppInfo>()
        val pm = packageManager
        
        // Add Phone and Messages implicitly as they are in the dock, 
        // but user might want to see other selected apps.
        
        for (pkg in allowedPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                allowedAppsList.add(AppInfo(label, pkg, icon))
            } catch (e: PackageManager.NameNotFoundException) { }
        }
        
        if (allowedAppsList.isEmpty()) {
            Toast.makeText(this, "No apps allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.activity_main, null) 
        // Hack: we need a simple recycler view layout. 
        // Let's create a BottomSheetDialog programmatically.
        
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val rv = RecyclerView(this)
        rv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        rv.setPadding(32, 32, 32, 32)
        
        val allowedAdapter = AllowedAppAdapter(allowedAppsList) { app ->
             val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
             if (launchIntent != null) {
                 startActivity(launchIntent)
                 bottomSheetDialog.dismiss()
             } else {
                 Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
             }
        }
        rv.adapter = allowedAdapter
        bottomSheetDialog.setContentView(rv)
        bottomSheetDialog.show()
    }

    private fun playAlarm() {
        try {
            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            alarmPlayer = android.media.MediaPlayer.create(this, notification)
            if (alarmPlayer == null) {
                 // Fallback to notification sound
                 val notif = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                 alarmPlayer = android.media.MediaPlayer.create(this, notif)
            }
            alarmPlayer?.isLooping = true
            alarmPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
            alarmPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(intent, 0)
        
        // Find Dialer & SMS
        val defaultDialer = getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage
        val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        
        allApps.clear()
        for (resolveInfo in apps) {
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            
            // Skip this app
            if (packageName == this.packageName) continue
            
            val isDialer = packageName == defaultDialer
            val isSms = packageName == defaultSms
            val isDefaultApp = isDialer || isSms
            
            allApps.add(AppInfo(label, packageName, icon, isSelected = isDefaultApp, isFixed = isDefaultApp))
        }
        
        // Sort: Fixed first, then A-Z
        allApps.sortWith { a, b ->
            if (a.isFixed && !b.isFixed) -1
            else if (!a.isFixed && b.isFixed) 1
            else a.label.compareTo(b.label, ignoreCase = true)
        }
        
        filterApps("")
    }

    private fun filterApps(query: String) {
        displayApps.clear()
        if (query.isEmpty()) {
            displayApps.addAll(allApps)
        } else {
            val lowerQuery = query.lowercase(Locale.ROOT)
            displayApps.addAll(allApps.filter {
                it.label.lowercase(Locale.ROOT).contains(lowerQuery)
            })
        }
        adapter.notifyDataSetChanged()
        updateSelectedAppsSummary()
        
        // Keep app list container visible while searching
        val appListContainer = findViewById<LinearLayout>(R.id.appListContainer)
        val timerSection = findViewById<LinearLayout>(R.id.timerSection)
        if (appListContainer.visibility == View.VISIBLE || query.isNotEmpty()) {
            appListContainer.visibility = View.VISIBLE
            timerSection.visibility = View.GONE
        }
    }

    private fun updateSelectedAppsSummary() {
        // Always show Phone and Messages as selected
        // Filter from allApps (not displayApps) to always show ALL selected apps
        val selected = allApps.filter { it.isSelected && !it.isFixed }.map { it.label }
        val summary = if (selected.isEmpty()) {
            "Selected: Phone, Messages"
        } else {
            "Selected: Phone, Messages, " + selected.joinToString(", ")
        }
        // Update the TextView
        tvSelectedApps.text = summary
        tvSelectedApps.visibility = View.VISIBLE
    }

    private fun startFocusMode() {
        if (!isAccessibilityServiceEnabled()) {
             Toast.makeText(this, "Accessibility Service Required!", Toast.LENGTH_SHORT).show()
             startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
             return
        }

        val hours = npHour.value
        val minutes = npMinute.value
        
        if (hours == 0 && minutes == 0) {
            Toast.makeText(this, "Please set a duration", Toast.LENGTH_SHORT).show()
            return
        }

        // Build list of allowed apps
        val selectedAppsList = allApps.filter { it.isSelected && !it.isFixed }.map { it.label }
        
        val sb = StringBuilder()
        sb.append("You are about to lock your phone.\n\n")
        sb.append("Allowed Apps:\n")
        sb.append("• Phone\n")
        sb.append("• Messages\n")
        
        for (appLabel in selectedAppsList) {
            sb.append("• $appLabel\n")
        }
        
        if (selectedAppsList.isEmpty()) {
            sb.append("\n(No other apps selected)\n")
        }
        
        sb.append("\nAre you sure you want to start?")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirm Focus Mode")
            .setMessage(sb.toString())
            .setPositiveButton("Start Lock") { _, _ ->
                performLock(hours, minutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLock(hours: Int, minutes: Int) {
        val totalMinutes = (hours * 60) + minutes

        // Save to history
        val prefs = getSharedPreferences("lock_history", Context.MODE_PRIVATE)
        prefs.edit().putInt("last_duration", totalMinutes).apply()

        // Save State
        // IMPORTANT: We filter from ALL apps, not just displayed apps
        val selectedPackages = allApps.filter { it.isSelected }.map { it.packageName }.toMutableSet()
        
        // Ensure Default Dialer & SMS are ALWAYS allowed
        val defaultDialer = getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage
        if (defaultDialer != null) {
            selectedPackages.add(defaultDialer)
        }
        val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        if (defaultSms != null) {
            selectedPackages.add(defaultSms)
        }
        
        Utils.setAllowedApps(this, selectedPackages)
        
        val durationMillis = totalMinutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMillis
        Utils.setTimer(this, endTime, durationMillis)

        // Start File Service
        val serviceIntent = Intent(this, TimerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        updateUIState()
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
}
