package com.santhu.keepmyphoneout

object Constants {
    /**
     * List of social media apps to auto-select for blocking
     * NOTE: WhatsApp is intentionally EXCLUDED (user requested)
     */
    val BLOCKED_SOCIAL_APPS = setOf(
        // Major Global Platforms
        "com.facebook.katana",              // Facebook
        "com.facebook.orca",                // Facebook Messenger
        "com.facebook.lite",                // Facebook Lite
        "com.instagram.android",            // Instagram
        "com.instagram.lite",               // Instagram Lite
        "com.google.android.youtube",       // YouTube
        "com.google.android.apps.youtube.music", // YouTube Music
        "com.ss.android.ugc.trill",         // TikTok (International)
        "com.zhiliaoapp.musically",         // TikTok (Alternative package)
        
        // Messaging & Chat (except WhatsApp)
        "org.telegram.messenger",           // Telegram
        "org.telegram.messenger.web",       // Telegram X
        "com.snapchat.android",             // Snapchat
        "jp.naver.line.android",            // Line
        "com.kakao.talk",                   // KakaoTalk
        "com.viber.voip",                   // Viber
        "com.imo.android.imoim",            // IMO
        
        // Twitter/X & Alternatives
        "com.twitter.android",              // X (Twitter)
        "com.twitter.android.lite",         // X Lite
        "com.instagram.barcelona",          // Threads
        "com.bluesky.app.android",          // Bluesky
        "org.joinmastodon.android",         // Mastodon
        
        // Content & Video
        "com.reddit.frontpage",             // Reddit
        "com.pinterest",                    // Pinterest
        "tv.twitch.android.app",            // Twitch
        "com.caffeine.app",                 // Caffeine
        
        // Professional & Q&A
        "com.linkedin.android",             // LinkedIn
        "com.linkedin.android.lite",        // LinkedIn Lite
        "com.quora.android",                // Quora
        
        // Gaming & Community
        "com.discord",                      // Discord
        
        // Regional - China
        "com.ss.android.ugc.aweme",         // Douyin (Chinese TikTok)
        "com.tencent.mobileqq",             // QQ
        "com.tencent.mm",                   // WeChat
        "com.sina.weibo",                   // Weibo
        "com.baidu.tieba",                  // Baidu Tieba
        
        // Regional - Russia/CIS
        "com.vkontakte.android",            // VK (VKontakte)
        "ru.ok.android",                    // Odnoklassniki (OK.ru)
        
        // Regional - Other
        "com.zing.zalo",                    // Zalo (Vietnam)
        
        // Lifestyle & Emerging
        "com.bereal.ft",                    // BeReal
        "com.lemon8.android",               // Lemon8
        
        // Neighborhood & Local
        "com.nextdoor"                      // Nextdoor
    )

    /**
     * Check if an app is a social media app that should be blocked
     */
    fun isSocialMedia(packageName: String): Boolean {
        return BLOCKED_SOCIAL_APPS.contains(packageName)
    }
    
    /**
     * Apps that should NEVER be auto-blocked (always allowed)
     * WhatsApp is here because user explicitly requested it
     */
    val ALWAYS_ALLOWED_APPS = setOf(
        "com.whatsapp",                     // WhatsApp
        "com.whatsapp.w4b"                  // WhatsApp Business
    )
    
    fun isAlwaysAllowed(packageName: String): Boolean {
        return ALWAYS_ALLOWED_APPS.contains(packageName)
    }
}

