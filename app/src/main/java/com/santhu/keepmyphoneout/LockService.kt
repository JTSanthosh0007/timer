package com.santhu.keepmyphoneout

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class LockService : AccessibilityService() {

    private var launcherPackage: String? = null
    private var lastBlockTime: Long = 0
    private var lastBlockedPackage: String? = null
    private var lastToastTime: Long = 0
    private val BLOCK_COOLDOWN_MS = 1000L // 1 second cooldown to prevent flickering
    private val TOAST_COOLDOWN_MS = 3000L // 3 seconds between toasts
    
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackage = getLauncherPackage()
        
        // Configure service for maximum coverage
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        
        if (!Utils.isLocked(this)) return
        
        // If we're already on our app, do nothing
        if (packageName == this.packageName) {
            return
        }
        
        val allowedApps = Utils.getAllowedApps(this)
        
        // Check if this app is allowed
        if (isAppAllowed(packageName, allowedApps)) {
            return
        }
        
        // Debounce: Don't block the same package repeatedly within cooldown
        val currentTime = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && 
            currentTime - lastBlockTime < BLOCK_COOLDOWN_MS) {
            return
        }
        
        // Block this app
        lastBlockTime = currentTime
        lastBlockedPackage = packageName
        
        // Show appropriate message based on what was blocked
        showBlockedMessage(packageName)
        
        // Just go home and bring our app - don't keep spamming
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        // Small delay before bringing app to front
        handler.postDelayed({
            if (Utils.isLocked(this)) {
                bringAppToFront()
            }
        }, 100)
    }
    
    private fun showBlockedMessage(packageName: String) {
        val currentTime = System.currentTimeMillis()
        
        // Don't spam toasts
        if (currentTime - lastToastTime < TOAST_COOLDOWN_MS) {
            return
        }
        
        lastToastTime = currentTime
        
        val message = when {
            // Notification shade / System UI
            packageName == "com.android.systemui" -> 
                "🔒 Focus Mode Active!\nNotifications blocked until timer ends."
            
            // Launcher / Home screen
            isLauncherPackage(packageName) -> 
                "🔒 Stay Focused!\nUse only your selected apps."
            
            // Any other blocked app
            else -> 
                "🔒 App Blocked!\nUse your allowed apps or wait for timer to end."
        }
        
        handler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isLauncherPackage(packageName: String): Boolean {
        if (packageName == launcherPackage) return true
        
        val launchers = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.vivo.launcher",
            "com.huawei.android.launcher",
            "com.microsoft.launcher",
            "com.teslacoilsw.launcher",
            "com.lge.launcher3",
            "com.asus.launcher",
            "com.motorola.launcher3",
            "com.realme.launcher",
            "com.nothing.launcher"
        )
        return launchers.contains(packageName) || packageName.contains("launcher")
    }
    
    private fun isAppAllowed(packageName: String, allowedApps: Set<String>): Boolean {
        // Always allow our app
        if (packageName == this.packageName) return true
        
        // Allow user-selected apps
        if (allowedApps.contains(packageName)) return true
        
        // Allow input methods (keyboards)
        if (isInputMethod(packageName)) return true
        
        // Allow system file pickers (for uploads within allowed apps)
        if (isSystemPicker(packageName)) return true
        
        // Allow certain system components that shouldn't be blocked
        if (isEssentialSystemApp(packageName)) return true
        
        return false
    }
    
    private fun isEssentialSystemApp(packageName: String): Boolean {
        val essentials = setOf(
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
        return essentials.contains(packageName)
    }
    
    private fun bringAppToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or 
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isInputMethod(packageName: String): Boolean {
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val inputMethods = imm.enabledInputMethodList
            for (method in inputMethods) {
                if (method.packageName == packageName) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isSystemPicker(packageName: String): Boolean {
        val systemPickers = setOf(
            // Android System & Providers
            "com.android.documentsui",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.providers.downloads.ui",
            "com.android.externalstorage",
            "com.android.htmlviewer", // Sometimes used for previews
            "com.android.mtp", // MTP host
            
            // Google Apps & Providers
            "com.google.android.documentsui",
            "com.google.android.apps.docs",
            "com.google.android.apps.photos",
            "com.google.android.providers.media.module", // Google Media Provider
            "com.google.android.permissioncontroller", // Permission Dialogs
            "com.android.permissioncontroller",
            
            // Samsung
            "com.samsung.android.documentsui",
            "com.sec.android.app.myfiles",
            "com.samsung.android.provider.filteredprovider",
            "com.sec.android.provider.badge",
            "com.samsung.android.messaging", // Sometimes handles sharing
            
            // Xiaomi / Poco / Redmi
            "com.mi.android.globalFileexplorer",
            "com.miui.gallery",
            "com.miui.securitycenter", // Sometimes needed for permissions
            
            // Oppo / Realme / OnePlus
            "com.coloros.filemanager",
            "com.coloros.photos",
            "com.coloros.gallery3d",
            "com.oneplus.filemanager",
            "com.oneplus.gallery",
            
            // Vivo
            "com.vivo.filemanager",
            "com.vivo.gallery",
            
            // Huawei / Honor
            "com.huawei.filemanager",
            "com.huawei.photos",
            
            // Third-party common file managers
            "com.google.android.apps.nbu.files", // Files by Google
            "com.cx.fileexplorer",
            "com.mixplorer",
            "com.lonelycatgames.Xplore",
            "com.alphainventor.filemanager",
            
            // Camera apps (for "Take Photo" option)
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.samsung.android.camera",
            "com.sec.android.app.camera",
            "com.miui.camera",
            "com.oppo.camera",
            "com.oneplus.camera"
        )
        
        // Also check if it's a "provider" or "picker" loosely to catch others
        if (packageName.endsWith(".documentsui") || 
            packageName.contains("providers.media") || 
            packageName.contains("filemanager") || 
            packageName.contains("gallery")) {
            return true
        }
        
        return systemPickers.contains(packageName)
    }

    override fun onInterrupt() {
        // Required method
    }

    private fun getLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }
}
