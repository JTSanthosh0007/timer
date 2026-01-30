package com.santhu.keepmyphoneout

import android.app.Application
import com.google.android.gms.ads.MobileAds

/**
 * Application class for Keep My Phone Out
 * Initializes AdMob and App Open Ads
 */
class KeepMyPhoneOutApp : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob SDK
        MobileAds.initialize(this) { initializationStatus ->
            // SDK initialized, load app open ad
            appOpenAdManager = AppOpenAdManager(this)
            appOpenAdManager.loadAd()
        }
    }
}
