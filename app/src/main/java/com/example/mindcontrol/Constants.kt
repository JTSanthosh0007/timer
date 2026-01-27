package com.example.mindcontrol

object Constants {
    val BLOCKED_SOCIAL_APPS = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca", // Messenger
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.pinterest",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.discord"
    )

    fun isSocialMedia(packageName: String): Boolean {
        return BLOCKED_SOCIAL_APPS.contains(packageName)
    }
}
