package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

@FunctionalInterface
public interface ValueCallback<T> {
    void onReceiveValue(T value);
}
