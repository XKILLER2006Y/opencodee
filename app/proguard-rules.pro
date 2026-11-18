-keepclassmembers class com.celoext.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Pure-Java xz (Termux .deb extraction): plain calls, keep names for readable stack traces.
-keep class org.tukaani.xz.** { *; }
