# Keep services alive
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.app.job.JobService
-keep class * extends android.service.notification.NotificationListenerService
-keep class * extends androidx.work.Worker

# Keep all classes in your package
-keep class com.android.mycalculator.** { *; }

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

# 1. PROTECT WORKMANAGER CLASSES
# If R8 obfuscates these, the background Android Job Scheduler will crash trying to find them.
-keep class androidx.work.Worker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class com.android.mycalculator.SyncWorker { *; }
-keep class com.android.mycalculator.HeartbeatWorker { *; }

# 2. PROTECT ANDROID COMPONENTS (Double Security)
# Ensures your receivers and services keep their exact names so intents don't fail.
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }

# 3. SILENCE SAFE WARNINGS
# Prevents the build from failing due to missing external library references we aren't using.
-dontwarn androidx.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.json.**
