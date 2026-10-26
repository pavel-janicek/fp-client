# Project specific ProGuard rules for FitPub Wear (:wear).
#
# kotlinx.serialization (mirrors app/proguard-rules.pro, scoped to the wear
# package): the sign-in relay (WearAuthMessage, Iteration 9b) and workout sync
# DTOs (Iteration 9e) are only touched via generated serializers, which R8 full
# mode strips in release builds. Without these, decoding the phone's credential
# reply throws and "Sign in with phone" silently does nothing on release builds.
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fpclient.android.wear.**$$serializer { *; }
-keepclassmembers class com.fpclient.android.wear.** {
    *** Companion;
}
-keepclasseswithmembers class com.fpclient.android.wear.** {
    kotlinx.serialization.KSerializer serializer(...);
}


# Room, required by WorkManager (WorkoutSyncScheduler, WatchWorkoutSyncWorker). Same rule and
# reason as app/proguard-rules.pro: WorkManager's androidx.startup initializer creates
# WorkDatabase_Impl reflectively, R8 strips the constructor it never sees called, and the
# minified watch app dies on every launch with
#   NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []
# (Play rejected wear versionCode 62 for exactly this: "Your app crashed after installation").
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**
