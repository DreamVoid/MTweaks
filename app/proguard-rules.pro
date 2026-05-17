# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\sdk\android-sdk-windows/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# libxposed API 102 module entry points and metadata
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-dontwarn io.github.libxposed.annotation.**

# Entry points are instantiated reflectively by the framework
-keep class me.dreamvoid.mtweaks.xposed.core.MTweaksModule { public <init>(); }
-keep class me.dreamvoid.mtweaks.xposed.core.ModuleRegistry { public <init>(); }

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
