package com.pulse.bluetoothdisable.privacy

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/** Renders only a sealed in-memory snapshot. The browser engine cannot perform network I/O. */
internal fun configurePrivacyWebView(
    view: WebView,
    page: PrivacyPageLoader.Page,
    onReady: () -> Unit,
    onFailure: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    @Suppress("DEPRECATION")
    view.settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        blockNetworkLoads = true
        cacheMode = WebSettings.LOAD_NO_CACHE
        safeBrowsingEnabled = true
        defaultTextEncodingName = "UTF-8"
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
    }
    // Per-view setting only: do not change/clear process-wide cookies or storage.
    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
    view.setDownloadListener { _, _, _, _, _ -> view.post { onFailure() } }
    view.webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView?, dialog: Boolean, gesture: Boolean, result: Message?): Boolean = false
        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
        override fun onShowFileChooser(
            webView: WebView?, callback: ValueCallback<Array<android.net.Uri>>, params: FileChooserParams?,
        ): Boolean {
            callback.onReceiveValue(null)
            return true
        }
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
            val resource = page.resource(request.url.toString(), request.isForMainFrame, request.method)
            if (resource == null) return blockedPrivacyResponse()
            return WebResourceResponse(
                resource.mime, "UTF-8", 200, "OK",
                mapOf(
                    "Content-Security-Policy" to PrivacyPageLoader.CSP,
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff",
                    "Referrer-Policy" to "no-referrer",
                ),
                ByteArrayInputStream(resource.bytes),
            )
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame || request.method != "GET") return true
            val target = try {
                PrivacyUrlPolicy.require(request.url.toString(), PrivacyUrlPolicy.Kind.DOCUMENT).toString()
            } catch (_: IllegalArgumentException) { return true }
            if (page.isCurrentDocument(target)) return false // Same-document anchors; no native request.
            onNavigate(target) // New document must be fetched/validated before rendering.
            return true
        }

        // Legacy navigations are blocked, never delegated to ActivityManager.
        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url == null || !page.isCurrentDocument(url)) {
                view?.stopLoading()
                onFailure()
            }
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            if (url != null && page.isCurrentDocument(url)) onReady()
        }
        override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
            if (request.isForMainFrame) onFailure()
        }
        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, response: WebResourceResponse?) {
            if (request.isForMainFrame) onFailure()
        }
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
            handler.cancel()
            onFailure()
        }
        @android.annotation.TargetApi(27)
        override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, response: SafeBrowsingResponse) {
            response.backToSafety(true)
            onFailure()
        }
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            // Remove the unusable renderer immediately, then let Compose show the retry UI.
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
            onFailure()
            return true
        }
    }
    // loadUrl is not covered by shouldOverrideUrlLoading: explicitly verify the starting URL.
    val initial = PrivacyUrlPolicy.require(page.url, PrivacyUrlPolicy.Kind.DOCUMENT).toString()
    check(page.resource(initial, true, "GET") != null)
    view.loadUrl(initial)
}

private fun blockedPrivacyResponse() = WebResourceResponse(
    "text/plain", "UTF-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0)),
)

internal fun createPrivacyWebView(context: Context) = WebView(context)

internal fun releasePrivacyWebView(view: WebView) {
    view.stopLoading()
    view.onPause()
    view.clearHistory()
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    view.destroy()
}
