# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep MediaCodec classes
-keep class android.media.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
