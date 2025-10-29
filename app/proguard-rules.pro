# Keep Hilt generated classes

-keep class dagger.hilt.** { *; }
-keep class * extends android.app.Application
-keep class com.example.aichat.** { *; }

# Keep kotlinx.serialization

-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

# Retrofit / OkHttp models

-dontwarn okhttp3.**
-dontwarn okio.**

# Keep GLSL raw resources names

-keepclassmembers class ** {
    public static final int orb_vertex;
    public static final int orb_fragment;
}
