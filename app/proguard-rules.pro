# Proguard rules for PC Control App
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-keep class com.example.pccontrol.** { *; }
-dontwarn java.lang.invoke.**
