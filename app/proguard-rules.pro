# Waffle R8 rules.
# These libraries use reflection or native entry points that R8 cannot see.

# Offline routing
-keep class com.graphhopper.** { *; }
-keep class org.locationtech.jts.** { *; }
-dontwarn com.graphhopper.**
-dontwarn org.locationtech.jts.**
-dontwarn org.slf4j.**

# Map rendering
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Gson / Retrofit payloads
-keep class com.noobdev.waffle.data.local.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.noobdev.waffle.data.remote.** { *; }
-keep class com.noobdev.waffle.data.model.** { *; }
-keep class com.noobdev.waffle.feature.shops.data.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# Kotlin serialization
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.noobdev.waffle.**$$serializer { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Firebase / Firestore use reflection for models and entry points.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep line information for crash reports in release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
