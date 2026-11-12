package com.gleam.windowcleaning

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val value = WebChromeClient.FileChooserParams.parseResult(
            result.resultCode,
            result.data
        )
        fileCallback?.onReceiveValue(value)
        fileCallback = null
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "window.onNotificationPermission&&onNotificationPermission($granted)", null
            )
        }
    }

    /** Only enabled while the page has something to close, so predictive back works otherwise. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView.evaluateJavascript("window.nativeBack&&nativeBack()", null)
        }
    }

    fun setBackActive(active: Boolean) {
        runOnUiThread { backCallback.isEnabled = active }
    }

    fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    // ---- Version rollback: the APK also carries the previous release's page ----
    private val versionPrefs by lazy { getSharedPreferences("gleam_version", MODE_PRIVATE) }

    fun hasPreviousVersion(): Boolean =
        runCatching { assets.open("previous/index.html").close(); true }.getOrDefault(false)

    fun previousVersionName(): String =
        runCatching { assets.open("previous/VERSION.txt").bufferedReader().use { it.readText().trim() } }.getOrDefault("")

    fun currentVersionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")

    fun usingPreviousVersion(): Boolean =
        versionPrefs.getBoolean("usePrevious", false) && hasPreviousVersion()

    private fun pageUrl(): String =
        if (usingPreviousVersion()) "https://appassets.androidplatform.net/assets/previous/index.html"
        else "https://appassets.androidplatform.net/assets/index.html"

    fun switchVersion(previous: Boolean) {
        versionPrefs.edit().putBoolean("usePrevious", previous && hasPreviousVersion()).apply()
        runOnUiThread { webView.loadUrl(pageUrl()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        BriefWorker.ensureChannel(this)
        ReminderScheduler.refresh(this)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(6, 26, 32))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    deliverPendingNativeState()
                    if (url?.contains("/assets/previous/") == true) view?.evaluateJavascript(PREVIOUS_BANNER_JS, null)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net") {
                        return false
                    }

                    return when (uri.scheme?.lowercase()) {
                        "sms", "smsto", "tel", "mailto", "geo" -> {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            true
                        }
                        "http", "https" -> {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            true
                        }
                        else -> false
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    return try {
                        filePicker.launch(fileChooserParams.createIntent())
                        true
                    } catch (_: Exception) {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        false
                    }
                }
            }

            addJavascriptInterface(
                GleamNativeBridge(this@MainActivity),
                "GleamNative"
            )
        }

        // The WebView sits in a container so the keyboard can genuinely shrink it.
        // (Padding on the WebView itself doesn't resize the page on many devices,
        // e.g. Samsung, so the keyboard covered text boxes.)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#04141f"))
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)

        // Edge-to-edge: the page handles system bars via env(safe-area-inset-*);
        // only the keyboard needs native help: lift the whole WebView above it.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val lift = if (insets.isVisible(WindowInsetsCompat.Type.ime())) ime else 0  // full keyboard height: never let it overlap the page
            view.setPadding(0, 0, 0, lift)
            // tell the page so it can keep the focused box in view
            webView.evaluateJavascript("window.onKeyboard&&window.onKeyboard(${if (lift > 0) "true" else "false"})", null)
            insets
        }

        if (savedInstanceState == null) {
            webView.loadUrl(pageUrl())
        } else {
            webView.restoreState(savedInstanceState)
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun deliverPendingNativeState() {
        if (!::webView.isInitialized) return
        val pending = NativeStateStore.pendingState(this)
        if (pending.isBlank()) return
        val encoded = JSONObject.quote(pending)
        webView.evaluateJavascript(
            "if(window.receiveNativeState){window.receiveNativeState($encoded);}",
            null
        )
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.onNativeResume&&onNativeResume()", null)
            webView.postDelayed({ deliverPendingNativeState() }, 250)
        }
    }

    override fun onPause() {
        // write any debounced save before Android can kill the process
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.flushSave&&flushSave()", null)
        }
        super.onPause()
    }

    fun requestNotificationPermissionIfNeeded() = runOnUiThread {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun performGleamHaptic() {
        runOnUiThread {
            val feedback = if (Build.VERSION.SDK_INT >= 30) {
                android.view.HapticFeedbackConstants.CONFIRM
            } else {
                android.view.HapticFeedbackConstants.VIRTUAL_KEY
            }
            webView.performHapticFeedback(feedback)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.removeJavascriptInterface("GleamNative")
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /** Shown on top of the previous version's page, so Ant can always get back to the newest one. */
        const val PREVIOUS_BANNER_JS = """
(function(){
  if(document.getElementById('gleamPrevBar')) return;
  var b=document.createElement('div'); b.id='gleamPrevBar';
  b.style.cssText='position:fixed;left:10px;right:10px;top:calc(env(safe-area-inset-top) + 6px);z-index:99999;display:flex;align-items:center;gap:10px;padding:8px 10px 8px 14px;border-radius:14px;background:rgba(245,214,123,.95);color:#2b2000;font:600 13px/1.25 system-ui,sans-serif;box-shadow:0 6px 20px rgba(0,0,0,.4)';
  b.innerHTML='<span style="flex:1">Using the previous version</span><button style="border:0;border-radius:10px;padding:9px 12px;background:#2b2000;color:#ffe9a8;font:700 13px system-ui,sans-serif">Back to newest</button>';
  b.querySelector('button').onclick=function(){ try{ GleamNative.useVersion('newest'); }catch(e){} };
  document.body.appendChild(b);
  var app=document.getElementById('app');
  if(app){ app.style.marginTop='58px'; app.style.height='calc(100dvh - 58px)'; }
})();
"""
    }
}
