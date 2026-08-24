package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

public final class WebSettings {
    private boolean databaseEnabled;
    private boolean domStorageEnabled;
    private boolean javaScriptEnabled;
    private boolean loadWithOverviewMode;
    private boolean useWideViewPort;
    private String userAgentString;

    WebSettings() {
    }

    public void setDatabaseEnabled(boolean enabled) {
        databaseEnabled = enabled;
    }

    public void setDomStorageEnabled(boolean enabled) {
        domStorageEnabled = enabled;
    }

    public void setJavaScriptEnabled(boolean enabled) {
        javaScriptEnabled = enabled;
    }

    public void setLoadWithOverviewMode(boolean enabled) {
        loadWithOverviewMode = enabled;
    }

    public void setUseWideViewPort(boolean enabled) {
        useWideViewPort = enabled;
    }

    public void setUserAgentString(String value) {
        userAgentString = value;
    }

    public boolean getDatabaseEnabled() {
        return databaseEnabled;
    }

    public boolean getDomStorageEnabled() {
        return domStorageEnabled;
    }

    public boolean getJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    public boolean getLoadWithOverviewMode() {
        return loadWithOverviewMode;
    }

    public boolean getUseWideViewPort() {
        return useWideViewPort;
    }

    public String getUserAgentString() {
        return userAgentString;
    }
}
