package com.santhu.keepmyphoneout

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Interstitial Ad Manager - Shows full-screen ads at natural breaks
 * Best used: After focus session ends
 */
object InterstitialAdManager {

    private const val TAG = "InterstitialAdManager"
    private const val AD_UNIT_ID = "ca-app-pub-4060024795112786/9547233630"
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    /**
     * Load an interstitial ad
     * Call this early (e.g., when app starts or after showing an ad)
     */
    fun loadAd(activity: Activity) {
        if (isLoading || interstitialAd != null) {
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            activity,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Log.d(TAG, "Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Log.e(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Show the interstitial ad if available
     * @param activity The activity to show the ad in
     * @param onAdDismissed Callback when ad is dismissed or fails to show
     */
    fun showAd(activity: Activity, onAdDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        
        if (ad == null) {
            Log.d(TAG, "Interstitial Ad not ready, loading...")
            loadAd(activity)
            onAdDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad dismissed")
                interstitialAd = null
                loadAd(activity) // Preload next ad
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial Ad failed to show: ${adError.message}")
                interstitialAd = null
                loadAd(activity)
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial Ad shown")
            }
        }

        ad.show(activity)
    }

    /**
     * Check if an ad is ready to show
     */
    fun isAdReady(): Boolean = interstitialAd != null
}
