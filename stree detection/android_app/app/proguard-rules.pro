# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep TensorFlow Lite classes
-keep class org.tensorflow.** { *; }
-keepclassmembers class org.tensorflow.** { *; }

# Keep model classes
-keep class com.example.stressdetection.StressInference { *; }
-keep class com.example.stressdetection.StressInference$* { *; }

# Keep Gson classes for history serialization
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
