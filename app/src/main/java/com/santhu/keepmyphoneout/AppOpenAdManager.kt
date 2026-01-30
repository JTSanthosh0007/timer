package com.santhu.keepmyphoneout

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * App Open Ad Manager - Shows ads when app comes to foreground
 */
class AppOpenAdManager(private val application: Application) : Application.ActivityLifecycleCallbacks, LifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_UNIT_ID = "ca-app-pub-4060024795112786/9626435230"
        private const val AD_EXPIRY_HOURS = 4 // Ads expire after 4 hours
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Load an App Open Ad
     */
    fun loadAd() {
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        AppOpenAd.load(
            application,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App Open Ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Check if ad is available and not expired
     */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && !isAdExpired()
    }

    /**
     * Check if ad has expired (4 hours)
     */
    private fun isAdExpired(): Boolean {
        val dateDifference = Date().time - loadTime
        val numHours = dateDifference / 3600000
        return numHours >= AD_EXPIRY_HOURS
    }

    /**
     * Show the App Open Ad if available
     */
    fun showAdIfAvailable(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (isShowingAd) {
            Log.d(TAG, "Already showing an ad")
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG, "Ad not available, loading new one")
            loadAd()
            onAdDismissed()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAd() // Load next ad
                onAdDismissed()
                Log.d(TAG, "App Open Ad dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd()
                onAdDismissed()
                Log.e(TAG, "App Open Ad failed to show: ${adError.message}")
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                Log.d(TAG, "App Open Ad shown")
            }
        }

        appOpenAd?.show(activity)
    }

    /**
     * Called when app comes to foreground
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        currentActivity?.let { activity ->
            // Don't show ad if timer is running (user is in focus mode)
            if (!Utils.isLocked(activity)) {
                showAdIfAvailable(activity)
            }
        }
    }

    // Activity Lifecycle Callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        // Don't show ad on splash/onboarding
        if (activity !is OnboardingActivity) {
            currentActivity = activity
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is OnboardingActivity) {
            currentActivity = activity
        }
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }
}
