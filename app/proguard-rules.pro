# Model data classes & serialization
-keep class md.sime.agenda.model.** { *; }

# WebView JavaScript bridge callbacks
-keepattributes *Annotation*,JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Lifecycle 2.8.x + Compose UI 1.6.x LocalLifecycleOwner lookup under R8
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static androidx.compose.runtime.ProvidableCompositionLocal getLocalLifecycleOwner();
}

# Jsoup HTML parser
-keeppackagenames org.jsoup.**
-dontwarn org.jsoup.**

# AndroidX Security Crypto / Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
