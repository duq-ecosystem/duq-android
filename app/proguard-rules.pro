# ============================================================
# ProGuard/R8 Rules for Duq Android
# ============================================================

# -----------------------------------------------------------
# PORCUPINE (Wake Word Detection)
# -----------------------------------------------------------
-keep class ai.picovoice.** { *; }
-keepclassmembers class ai.picovoice.** { *; }

# -----------------------------------------------------------
# SILERO VAD (Voice Activity Detection)
# -----------------------------------------------------------
-keep class com.konovalov.vad.** { *; }
-keepclassmembers class com.konovalov.vad.** { *; }

# -----------------------------------------------------------
# OKHTTP & OKIO (Networking)
# -----------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# -----------------------------------------------------------
# KOTLIN COROUTINES
# -----------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# -----------------------------------------------------------
# EXOPLAYER / MEDIA3 (Audio Playback)
# -----------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# -----------------------------------------------------------
# HILT (Dependency Injection)
# -----------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.EarlyEntryPoint class *
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <init>(...);
}

# -----------------------------------------------------------
# APPAUTH (OAuth2/OIDC)
# -----------------------------------------------------------
-keep class net.openid.appauth.** { *; }
-keepclassmembers class net.openid.appauth.** { *; }

# -----------------------------------------------------------
# GSON (JSON Parsing)
# -----------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep API models (prevent field name obfuscation)
-keep class com.duq.android.network.ApiModels { *; }
-keep class com.duq.android.network.ApiModels$* { *; }

# -----------------------------------------------------------
# ROOM DATABASE
# -----------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# -----------------------------------------------------------
# DATA MODELS (Prevent obfuscation of serialized fields)
# -----------------------------------------------------------
-keep class com.duq.android.data.model.** { *; }
-keep class com.duq.android.data.local.entities.** { *; }
-keep class com.duq.android.network.** { *; }

# -----------------------------------------------------------
# COMPOSE
# -----------------------------------------------------------
-dontwarn androidx.compose.**

# -----------------------------------------------------------
# LIFECYCLE
# -----------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# -----------------------------------------------------------
# GENERAL ANDROID
# -----------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep parcelables
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# -----------------------------------------------------------
# ERROR HANDLING (Keep for crash reporting)
# -----------------------------------------------------------
-keep class com.duq.android.error.** { *; }
