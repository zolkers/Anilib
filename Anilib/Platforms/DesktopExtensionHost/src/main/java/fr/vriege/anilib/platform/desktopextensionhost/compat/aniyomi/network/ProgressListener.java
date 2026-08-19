package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

public interface ProgressListener {
    void update(long bytesRead, long contentLength, boolean done);
}
