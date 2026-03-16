-keepattributes *Annotation*

# NovaCut models (Room entities + serialization)
-keep class com.novacut.editor.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.DatabaseConfiguration

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.internal.**

# Media3 (ExoPlayer, Transformer, Effect)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Coil
-dontwarn coil.**

# Suppress common warnings
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
