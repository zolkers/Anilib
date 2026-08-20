package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.ProgressListener;

import java.util.Objects;
import kotlin.jvm.internal.DefaultConstructorMarker;

public class Page implements ProgressListener {
    private final int index;
    private final String url;
    private String imageUrl;
    private Uri uri;
    private State status = State.QUEUE;
    private int progress;

    public Page(int index, String url, String imageUrl, Uri uri) {
        this.index = index;
        this.url = Objects.requireNonNull(url, "url");
        this.imageUrl = imageUrl;
        this.uri = uri;
    }

    public Page(
            int index,
            String url,
            String imageUrl,
            Uri uri,
            int mask,
            DefaultConstructorMarker marker) {
        this(index, (mask & 2) == 0 ? url : "", (mask & 4) == 0 ? imageUrl : null,
                (mask & 8) == 0 ? uri : null);
    }

    public int getIndex() { return index; }
    public String getUrl() { return url; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String value) { imageUrl = value; }
    public Uri getUri() { return uri; }
    public void setUri(Uri value) { uri = value; }
    public int getNumber() { return index + 1; }
    public State getStatus() { return status; }
    public void setStatus(State value) { status = Objects.requireNonNull(value, "value"); }
    public int getProgress() { return progress; }
    public void setProgress(int value) { progress = value; }

    @Override
    public void update(long bytesRead, long contentLength, boolean done) {
        progress = contentLength > 0 ? Math.toIntExact(100L * bytesRead / contentLength) : -1;
    }

    public enum State {
        QUEUE,
        LOAD_PAGE,
        DOWNLOAD_IMAGE,
        READY,
        ERROR
    }
}
