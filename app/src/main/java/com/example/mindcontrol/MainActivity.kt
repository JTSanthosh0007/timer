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
    private lateinit var setupLayout: ScrollView
    private lateinit var lockedLayout: LinearLayout
    private lateinit var tvTimer: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var npHour: NumberPicker
    private lateinit var npMinute: NumberPicker
    private lateinit var progressTimer: CircularProgressIndicator

    private val allApps = mutableListOf<AppInfo>()
    private val displayApps = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter
    
    // Handler for smooth UI updates
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Check state and update UI
            val locked = Utils.isLocked(this@MainActivity)
            updateUIState()
            
            // Continue loop only if still locked
            if (locked) {
                 handler.postDelayed(this, 1000)
            }
        }
    }
    
    // Receiver for timer updates
    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Utils.ACTION_TIMER_TICK -> {
                    // Optional: We can rely on local handler for smoother UI
                }
                Utils.ACTION_TIMER_FINISHED -> {
                    updateUIState()
                    Toast.makeText(this@MainActivity, "Focus Session Complete!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        rvApps = findViewById(R.id.rvApps)
        etSearch = findViewById(R.id.etSearch)
        btnStart = findViewById(R.id.btnStart)
        setupLayout = findViewById(R.id.setupLayout)
        lockedLayout = findViewById(R.id.lockedLayout)
        tvTimer = findViewById(R.id.tvTimer)
        progressTimer = findViewById(R.id.progressTimer)
        tvInstruction = findViewById(R.id.tvInstruction)
        
        npHour = findViewById(R.id.npHour)
        npMinute = findViewById(R.id.npMinute)
        
        // Setup Pickers
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
            // Selection changed, optional logic here
        }
        rvApps.adapter = adapter

        btnStart.setOnClickListener { startFocusMode() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
        })
        
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
            updateUIState()
        } else {
            loadApps()
        }
    }

    override fun onBackPressed() {
        if (Utils.isLocked(this)) {
            // Block back button
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
        
        // Start local UI update loop
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        
        // Register receiver for UI updates
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
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(timerReceiver)
    }

    private fun updateUIState() {
        if (Utils.isLocked(this)) {
            setupLayout.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            
            // Initial text set just in case update hasn't arrived
            val endTime = Utils.getEndTime(this)
            val totalDuration = Utils.getTotalDuration(this)
            val remaining = endTime - System.currentTimeMillis()
            
            if (remaining > 0) {
                 tvTimer.text = Utils.formatTime(remaining)
                 // Progress is handled by max 1000
                 if (totalDuration > 0) {
                     val progress = ((remaining.toFloat() / totalDuration.toFloat()) * 1000).toInt()
                     progressTimer.progress = progress
                 }
            } else {
                 tvTimer.text = "00:00:00"
                 progressTimer.progress = 0
            }
            
            // Load Allowed Apps for the Launcher
            val allowedPackages = Utils.getAllowedApps(this)
            val allowedAppsList = mutableListOf<AppInfo>()
            val pm = packageManager
            for (pkg in allowedPackages) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    allowedAppsList.add(AppInfo(label, pkg, icon))
                } catch (e: PackageManager.NameNotFoundException) {
                    // Ignore uninstallable apps
                }
            }
            
            val rvAllowed = findViewById<RecyclerView>(R.id.rvAllowedApps)
            // Use specific layout manager for this recycler view if not already set or reuse logic
            if (rvAllowed.layoutManager == null) {
                rvAllowed.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
            }
            
            val allowedAdapter = AllowedAppAdapter(allowedAppsList) { app ->
                 val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                 if (launchIntent != null) {
                     startActivity(launchIntent)
                 } else {
                     Toast.makeText(this, "Cannot launch app", Toast.LENGTH_SHORT).show()
                 }
            }
            rvAllowed.adapter = allowedAdapter
            
        } else {
            setupLayout.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val apps = pm.queryIntentActivities(intent, 0)
        
        // Find Dialer
        val defaultDialer = getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage
        
        allApps.clear()
        for (resolveInfo in apps) {
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            
            // Skip this app
            if (packageName == this.packageName) continue
            
            val isDialer = packageName == defaultDialer
            allApps.add(AppInfo(label, packageName, icon, isSelected = isDialer, isFixed = isDialer))
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
        
        val totalMinutes = (hours * 60) + minutes

        // Save State
        // IMPORTANT: We filter from ALL apps, not just displayed apps
        val selectedPackages = allApps.filter { it.isSelected }.map { it.packageName }.toSet()
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
