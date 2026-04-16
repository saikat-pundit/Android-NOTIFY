# ===============================================
# CUSTOM PROGUARD RULES FOR STEALTH CALCULATOR
# ===============================================

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
