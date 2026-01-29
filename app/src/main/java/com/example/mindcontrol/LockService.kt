package com.example.mindcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class LockService : AccessibilityService() {

    private var launcherPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        launcherPackage = getLauncherPackage()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (Utils.isLocked(this)) {
                val allowedApps = Utils.getAllowedApps(this)
                
                // Always allow:
                // 1. This app
                // 2. System UI (notifications, etc)
                // 3. The Launcher (home screen) - otherwise you can't navigate
                // 4. The User selected apps
                // 5. Input Method Editors (Keyboards) - usually hard to detect by package here, but important. 
                //    However, blocking usually happens on Activity window. Keyboards are usually windows but might not trigger window_state_changed in a way that blocks them easily, or they are overlaid. 
                //    We'll assume strict blocking of "Activites".
                
                // Strict Blocking Mode:
                // 1. Allow this app (MainActivity needs to be usable as the launcher)
                // 2. Allow SYSTEM UI (Notifications, Volume, etc)
                // 3. ALLOW Launcher to prevent closing loops (but blocked apps launched from it will still be caught)
                // 4. Allow selected apps
                
                // NEW: We now block the Launcher package too.
                // This creates a "Seamless Pin" by preventing the user from going to the home screen.
                // They can only use Keep My Phone Out or their Selected Apps.
                
                if (packageName == this.packageName || 
                    packageName == "com.android.systemui" || 
                    allowedApps.contains(packageName) ||
                    isInputMethod(packageName)
                ) {
                    return
                }

                // Block!
                performGlobalAction(GLOBAL_ACTION_HOME)
                
                // Immediately bring our app to front to discourage usage
                 val intent = Intent(this, MainActivity::class.java)
                 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                 startActivity(intent)
            }
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
