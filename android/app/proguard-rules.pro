# Keep the WebView bridge callable from JavaScript if minification is ever enabled.
-keepclassmembers class com.gleam.windowcleaning.GleamNativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
