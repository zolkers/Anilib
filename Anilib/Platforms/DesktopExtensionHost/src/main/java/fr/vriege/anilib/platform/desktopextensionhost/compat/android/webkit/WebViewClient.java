package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

public class WebViewClient {
    public WebViewClient() {}

    public void onPageFinished(WebView view, String url) {}

    public void onPageStarted(WebView view, String url, Object favicon) {}

    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return null;
    }
}
