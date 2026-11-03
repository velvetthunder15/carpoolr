# Keep defaults; Compose and Backdrop ship their own consumer rules.

# Ktor / supabase-kt reference JVM-only classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
# Keep @Serializable models used for RPC results.
-keep,includedescriptorclasses class com.carpoolr.app.data.**$$serializer { *; }
-keepclassmembers class com.carpoolr.app.data.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
