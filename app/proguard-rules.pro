# Add project specific ProGuard rules here.
-keep class com.stresssense.app.data.models.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn com.google.mlkit.**
