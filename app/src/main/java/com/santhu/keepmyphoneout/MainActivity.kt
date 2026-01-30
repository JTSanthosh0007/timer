package com.santhu.keepmyphoneout

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
    
    // New bindings for UI control
    private lateinit var tvFocusLabel: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnHistoryTop: TextView
    // private lateinit var bottomNav: LinearLayout (Removed)
    private lateinit var btnStartContainer: LinearLayout
    private lateinit var segmentedControl: LinearLayout

    private lateinit var endedLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var tvEndTimeValue: TextView
    private var alarmPlayer: android.media.MediaPlayer? = null
    private var isTimerJustFinished = false
    
    private val allApps = mutableListOf<AppInfo>()
    private val displayApps = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter
    
    // Banner Ad
    private lateinit var adViewBanner: com.google.android.gms.ads.AdView
    

    
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
                    
                    // Show interstitial ad when session ends (great revenue opportunity!)
                    InterstitialAdManager.showAd(this@MainActivity) {
                        // After ad is dismissed, show the ended screen
                        isTimerJustFinished = true
                        playAlarm()
                        updateUIState()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        // Bind Views
        // Bind Views
        rvApps = findViewById(R.id.rvApps)
        etSearch = findViewById(R.id.etSearch)
        btnStart = findViewById(R.id.btnStart)
        tvSelectedApps = findViewById(R.id.tvSelectedApps)
        setupLayout = findViewById(R.id.setupLayout)
        lockedLayout = findViewById(R.id.lockedLayout)
        endedLayout = findViewById(R.id.endedLayout)
        tvTimer = findViewById(R.id.tvTimer)
        progressTimer = findViewById(R.id.progressTimer)
        tvEndTimeValue = findViewById(R.id.tvEndTimeValue)
        
        // Bind additional views for search mode
        tvFocusLabel = findViewById(R.id.tvFocusLabel)
        tvTitle = findViewById(R.id.tvTitle)
        btnHistoryTop = findViewById(R.id.btnHistoryTop)
        btnHistoryTop.setOnClickListener {
            // Using request code 102 for History restoration
            startActivityForResult(Intent(this, HistoryActivity::class.java), 102)
        }
        btnStartContainer = findViewById(R.id.btnStartContainer)
        segmentedControl = findViewById(R.id.segmentedControl)
        val tvSelectedTime = findViewById<TextView>(R.id.tvSelectedTime)
        val customPickerContainer = findViewById<LinearLayout>(R.id.customPickerContainer)
        
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
        // val btnTabSchedule (Removed)
        val timerSection = findViewById<LinearLayout>(R.id.timerSection)
        val appListContainer = findViewById<LinearLayout>(R.id.appListContainer)

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
        
        // Setup History Logic (Moved to HistoryActivity)

        // --- NEW PREMIUM UI LOGIC ---

        // Helper to update display
        fun updateDisplay(h: Int, m: Int) {
            tvSelectedTime.text = String.format("%02d:%02d:00", h, m)
        }

        // Chip Logic
        val chips = listOf(
            findViewById<Button>(R.id.chipCustom),
            findViewById<Button>(R.id.chip15m),
            findViewById<Button>(R.id.chip30m),
            findViewById<Button>(R.id.chip1h),
            findViewById<Button>(R.id.chip2h)
        )

        fun selectChip(selected: Button, h: Int, m: Int, isCustom: Boolean) {
            chips.forEach {
                it.setBackgroundResource(R.drawable.bg_segment_container)
                it.setTextColor(getColor(R.color.text_secondary))
            }
            selected.setBackgroundResource(R.drawable.bg_segment_selected)
            selected.setTextColor(getColor(R.color.text_primary))
            
            customPickerContainer.visibility = if (isCustom) View.VISIBLE else View.GONE
            
            if (!isCustom) {
                npHour.value = h
                npMinute.value = m
                updateDisplay(h, m)
            } else {
                updateDisplay(npHour.value, npMinute.value)
            }
        }

        findViewById<Button>(R.id.chipCustom).setOnClickListener { selectChip(it as Button, 0, 0, true) }
        findViewById<Button>(R.id.chip15m).setOnClickListener { selectChip(it as Button, 0, 15, false) }
        findViewById<Button>(R.id.chip30m).setOnClickListener { selectChip(it as Button, 0, 30, false) }
        findViewById<Button>(R.id.chip1h).setOnClickListener { selectChip(it as Button, 1, 0, false) }
        findViewById<Button>(R.id.chip2h).setOnClickListener { selectChip(it as Button, 2, 0, false) }

        // Default: Custom
        selectChip(findViewById(R.id.chipCustom), 0, 0, true)
        
        // Setup Pickers (White Text Hack)
        // ... (Keep existing Logic)
        npHour.minValue = 0
        npHour.maxValue = 23
        npMinute.minValue = 0
        npMinute.maxValue = 59
        npMinute.value = 0 // Default 0 min
        
        // Formatter for 00 style
        val formatter = NumberPicker.Formatter { i -> String.format("%02d", i) }
        npHour.setFormatter(formatter)
        npMinute.setFormatter(formatter)

        // Add Haptic/Click Feedback with explicit Vibration
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val soundListener = NumberPicker.OnValueChangeListener { picker, _, _ ->
             updateDisplay(npHour.value, npMinute.value)
             
             // Explicit Vibration for clearer feedback
             if (Build.VERSION.SDK_INT >= 26) {
                 vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, 255))
             } else {
                 vibrator.vibrate(80)
             }
             picker.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        }
        npHour.setOnValueChangedListener(soundListener)
        npMinute.setOnValueChangedListener(soundListener)

        rvApps.layoutManager = LinearLayoutManager(this)
        
        // Pass a lambda to count globally selected apps (excluding fixed ones like Dialer/SMS)
        adapter = AppAdapter(displayApps, {
            allApps.count { it.isSelected && !it.isFixed }
        }) {
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
        
        // Keep app list visible when search gets focus and toggle "Search Mode"
        etSearch.setOnFocusChangeListener { _, hasFocus ->
            toggleSearchMode(hasFocus)
            
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
        
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                etSearch.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                return@setOnEditorActionListener true
            }
            false
        }
        
        // Initial check
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please Enable Accessibility Service for Keep My Phone Out to help you focus.", Toast.LENGTH_LONG).show()
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
            // Removed startLockTask() to allow "Selected Apps" to work seamlessly 
            // and avoid the annoying "App is pinned" system dialog.
            // Accessibility Service (LockService) now handles all blocking.
            updateUIState()
        } else {
            // Reset selected apps when app opens and is NOT in a focus session
            // This ensures each session starts fresh with default selections
            Utils.clearAllowedApps(this)
            loadApps()
        }

        // Preload Interstitial Ad for session end
        InterstitialAdManager.loadAd(this)

        // Load Native Ad (AdMob initialization is handled in KeepMyPhoneOutApp)
        val adLoader = com.google.android.gms.ads.AdLoader.Builder(this, "ca-app-pub-4060024795112786/6134252090")
            .forNativeAd { nativeAd ->
                val adFrame = findViewById<android.widget.FrameLayout>(R.id.ad_frame) ?: return@forNativeAd
                
                // Inflate Native Ad View
                val adView = layoutInflater.inflate(R.layout.ad_native, null) as? com.google.android.gms.ads.nativead.NativeAdView
                if (adView == null) return@forNativeAd
                
                // Populate Assets
                adView.headlineView = adView.findViewById(R.id.ad_headline)
                adView.bodyView = adView.findViewById(R.id.ad_body)
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
                adView.iconView = adView.findViewById(R.id.ad_app_icon)
                adView.advertiserView = adView.findViewById(R.id.ad_advertiser)
                
                // Set Text
                (adView.headlineView as? TextView)?.text = nativeAd.headline
                (adView.bodyView as? TextView)?.text = nativeAd.body
                (adView.callToActionView as? Button)?.text = nativeAd.callToAction
                (adView.advertiserView as? TextView)?.text = nativeAd.advertiser
                
                // Set Icon
                val icon = nativeAd.icon
                if (icon != null) {
                    (adView.iconView as? android.widget.ImageView)?.setImageDrawable(icon.drawable)
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
        
        // Load Banner Ad
        adViewBanner = findViewById(R.id.adViewBanner)
        adViewBanner.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
    }

    override fun onBackPressed() {
        if (Utils.isLocked(this)) {
            // Block back button
            return
        }
        if (etSearch.hasFocus()) {
            etSearch.clearFocus()
            return
        }
        super.onBackPressed()
    }
    
    private fun toggleSearchMode(active: Boolean) {
        val visibility = if (active) View.GONE else View.VISIBLE
        
        tvFocusLabel.visibility = visibility
        tvTitle.visibility = visibility
        btnHistoryTop.visibility = visibility
        btnStartContainer.visibility = visibility
        segmentedControl.visibility = visibility
        findViewById<View>(R.id.timerSection).visibility = visibility
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
                         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                             progressTimer.setProgress(progress, true)
                         } else {
                             progressTimer.progress = progress
                         }
                    }
                } else {
                    tvTimer.text = "00:00:00"
                    progressTimer.progress = 0
                    // Don't call updateUIState here repeatedly or it might flicker the ended screen if not careful
                    // But if we are locked, we are locked.
                    // If time is up, the service triggers FINISHED.
                }
                
                uiHandler.postDelayed(this, 500)
            } else {
                // If not locked, stop updates
                uiHandler.removeCallbacks(this)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            val durationMins = data?.getIntExtra("restore_duration_mins", -1) ?: -1
            if (durationMins != -1) {
                // Restore Duration
                val h = durationMins / 60
                val m = durationMins % 60
                npHour.value = h
                npMinute.value = m
                
                // Update the visual timer display (HH:MM:00)
                val tvSelectedTime = findViewById<TextView>(R.id.tvSelectedTime)
                tvSelectedTime?.text = String.format("%02d:%02d:00", h, m)
                
                // Refresh Apps to show updated "isSelected" checkboxes
                loadApps()
                
                Toast.makeText(this, "Settings restored from history!", Toast.LENGTH_SHORT).show()
                
                // If it was more than 1h, it might have been a preset. 
                // We'll just set it to 'Custom' mode visually or let it be.
                // Best to switch to the App Block tab if apps were restored too?
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
            
            // Software Pinning is enforced by LockService (Accessibility),
            // which blocks the Home screen and unallowed apps automatically.

            val endTime = Utils.getEndTime(this)
            val totalDuration = Utils.getTotalDuration(this)
            val remaining = endTime - System.currentTimeMillis()
            
            // Format End Time
            val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            tvEndTimeValue.text = sdf.format(java.util.Date(endTime))
            
            if (remaining > 0) {
                 tvTimer.text = Utils.formatTime(remaining)
                 if (totalDuration > 0) {
                     val progress = ((remaining.toFloat() / totalDuration.toFloat()) * 1000).toInt()
                     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                         progressTimer.setProgress(progress, true)
                     } else {
                         progressTimer.progress = progress
                     }
                 }
            } else {
                 tvTimer.text = "00:00:00"
                 progressTimer.progress = 0
            }
            
            setupAllowedAppsDock()
            
        } else {
            setupLayout.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
        }
    }

    private var lastAllowedPackages: Set<String>? = null
    
    private fun setupAllowedAppsDock() {
        val allowedPackages = Utils.getAllowedApps(this)
        if (allowedPackages == lastAllowedPackages) return
        lastAllowedPackages = allowedPackages

        val allowedAppsList = mutableListOf<AppInfo>()
        val pm = packageManager
        
        // Identify Default Phone and SMS
        val defaultDialer = getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage
        val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(this)
        
        for (pkg in allowedPackages) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                allowedAppsList.add(AppInfo(label, pkg, icon))
            } catch (e: Exception) {}
        }
        
        // SORT: Phone -> SMS -> Alphabetical
        allowedAppsList.sortWith { a, b ->
            val isDialerA = a.packageName == defaultDialer
            val isDialerB = b.packageName == defaultDialer
            
            val isSmsA = a.packageName == defaultSms
            val isSmsB = b.packageName == defaultSms
            
            when {
                // PHONE First
                isDialerA && !isDialerB -> -1
                !isDialerA && isDialerB -> 1
                
                // MESSAGES Second
                isSmsA && !isSmsB -> -1
                !isSmsA && isSmsB -> 1
                
                // Then Alphabetical
                else -> a.label.compareTo(b.label, ignoreCase = true)
            }
        }
        
        val rvAllowed = findViewById<RecyclerView>(R.id.rvAllowedApps) ?: return
        rvAllowed.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllowed.adapter = AllowedAppAdapter(allowedAppsList) { app ->
             val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
             if (launchIntent != null) {
                 try {
                     startActivity(launchIntent)
                 } catch (e: Exception) {
                     Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
                 }
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
        
        val allowedApps = Utils.getAllowedApps(this)
        
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
            
            // Determine if app should be selected (allowed during focus)
            // NEW LOGIC: 
            // 1. Default apps (Phone, SMS) are always selected/allowed - user can't change
            // 2. ALL OTHER APPS are UNSELECTED by default (will be blocked)
            // 3. User must manually SELECT which apps to ALLOW
            val isSelected = when {
                isDefaultApp -> true  // Phone & SMS always allowed (fixed)
                else -> false  // All other apps are UNSELECTED by default
            }
            
            allApps.add(AppInfo(label, packageName, icon, isSelected = isSelected, isFixed = isDefaultApp))
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

        // Direct Lock (as requested: "it shoud not ask ok dricet it must pin")
        performLock(hours, minutes)
    }

    private fun performLock(hours: Int, minutes: Int) {
        val totalMinutes = (hours * 60) + minutes
        val durationMillis = totalMinutes * 60 * 1000L

        // Save to history
        Utils.addSessionToHistory(this, totalMinutes)

        // Save State
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
