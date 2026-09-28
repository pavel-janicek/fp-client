# Project specific ProGuard rules
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# kotlinx.serialization (rules from the kotlinx.serialization README; the
# package MUST match this project's DTO package, previously it pointed at a
# non-existent com.fitpub.android.* package and did not protect anything)
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fpclient.android.**$$serializer { *; }
-keepclassmembers class com.fpclient.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.fpclient.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# R8 full mode (the default with proguard-android-optimize.txt and AGP 8.x)
# strips generic signatures from classes that are not kept. Suspend functions
# are compiled to methods taking Continuation<Response<T>>, so if the generic
# argument is stripped Retrofit resolves the response type as java.lang.Object
# and fails at runtime with:
#   "Unable to create converter for class java.lang.Object for method <mangled>"
# (root cause of the 1.2.4 registration failure in release builds).
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Error Prone annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Room — REQUIRED by WorkManager (added in 2.1), and the cause of a hard crash loop.
#
# WorkManager stores its schedules in a Room database and initialises itself through
# androidx.startup's InitializationProvider, i.e. during `handleBindApplication`, before any
# Activity exists. It instantiates the Room-generated implementation reflectively:
#
#   WorkManagerInitializer -> WorkDatabase_Impl.<init>()
#
# R8 never sees that constructor being called (it is only reached by name, from generated
# code), so it strips it, and the minified build dies on every single launch with:
#
#   java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider
#   Caused by: java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
#
# The app therefore restarts in a loop and can never be opened, and nothing in the UI is
# involved at all. Debug builds are unaffected (R8 does not run), which is why this only
# appears in the release/F-Droid build.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
# Room's paging integration is referenced by WorkManager but unused here.
-dontwarn androidx.room.paging.**
