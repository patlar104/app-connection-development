# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep data classes for serialization
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep all serializable classes
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Timber
-keep class timber.log.Timber$* { *; }

# Keep encryption classes
-keep class dev.appconnect.core.encryption.** { *; }

# Keep WebSocket and Bluetooth classes
-keep class dev.appconnect.network.** { *; }

# Keep domain models
-keep class dev.appconnect.domain.model.** { *; }

# Keep service classes
-keep class dev.appconnect.service.** { *; }

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Android Keystore
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }

