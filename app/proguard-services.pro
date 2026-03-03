# Keep services alive
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.app.job.JobService
-keep class * extends android.service.notification.NotificationListenerService
-keep class * extends androidx.work.Worker

# Keep all classes in your package
-keep class com.example.notiflogger.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Don't obfuscate service names
-keepnames class * extends android.app.Service
-keepnames class * extends android.content.BroadcastReceiver

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Don't optimize too aggressively
-dontoptimize
-dontpreverify
-dontshrink

# Keep WorkManager
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Keep for reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
