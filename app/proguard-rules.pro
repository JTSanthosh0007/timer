# Add project specific ProGuard rules here.
# Keep our main app classes
-keep class com.santhu.keepmyphoneout.** { *; }

# Keep Gson classes (used for SharedPreferences JSON serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes used with Gson
-keep class com.santhu.keepmyphoneout.Utils$SessionRecord { *; }
-keep class com.santhu.keepmyphoneout.AppInfo { *; }

# Keep services
-keep class com.santhu.keepmyphoneout.TimerService { *; }
-keep class com.santhu.keepmyphoneout.LockService { *; }
-keep class com.santhu.keepmyphoneout.BootReceiver { *; }

# Keep AdMob
-keep class com.google.android.gms.ads.** { *; }

# Prevent R8 from stripping interface information
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
