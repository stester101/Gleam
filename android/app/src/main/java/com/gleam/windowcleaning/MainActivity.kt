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

        setContentView(webView)

        // Edge-to-edge: the page handles system bars via env(safe-area-inset-*);
        // only the keyboard needs native help, by shrinking the WebView above it.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, 0, 0, (ime - nav).coerceAtLeast(0))
            insets
        }

        if (savedInstanceState == null) {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
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
}
