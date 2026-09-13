# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Ads on the playstore branch are AdMob (Google Play services), whose consumer ProGuard
# rules ship inside its own AAR — no app-level keep rules needed.
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable, *Annotation*, EnclosingMethod

-dontwarn org.jetbrains.annotations.**

# ShellRunner reflects Shizuku's private newProcess(String[], String[], String) — the one-shot
# remote-shell channel (BattleGrounds_GFX's mechanism) that works when the user-service helper
# will not start. R8 must not rename/remove the method on the library class.
# Return type is ShizukuRemoteProcess (a java.lang.Process), not Process itself — spelling it
# as java.lang.Process matches nothing and R8 fails the build ("matches no class members").
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
