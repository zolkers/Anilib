package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import java.util.List;
import okhttp3.Headers;
import kotlin.jvm.internal.DefaultConstructorMarker;

public class Video {
    private String videoUrl;
    private final String videoTitle;
    private final Integer resolution;
    private final Integer bitrate;
    private final Headers headers;
    private final boolean preferred;
    private final List<Track> subtitleTracks;
    private final List<Track> audioTracks;
    private final List<TimeStamp> timestamps;
    private final List<?> mpvArgs;
    private final List<?> ffmpegStreamArgs;
    private final List<?> ffmpegVideoArgs;
    private final String internalData;
    private final boolean initialized;
    private String videoPageUrl = "";
    private State status = State.QUEUE;

    public Video(String videoUrl, String videoTitle, Integer resolution, Integer bitrate, Headers headers,
                 boolean preferred, List<Track> subtitleTracks, List<Track> audioTracks,
                 List<TimeStamp> timestamps, List<?> mpvArgs, List<?> ffmpegStreamArgs,
                 List<?> ffmpegVideoArgs, String internalData, boolean initialized) {
        this.videoUrl = videoUrl;
        this.videoTitle = videoTitle;
        this.resolution = resolution;
        this.bitrate = bitrate;
        this.headers = headers;
        this.preferred = preferred;
        this.subtitleTracks = List.copyOf(subtitleTracks);
        this.audioTracks = List.copyOf(audioTracks);
        this.timestamps = List.copyOf(timestamps);
        this.mpvArgs = List.copyOf(mpvArgs);
        this.ffmpegStreamArgs = List.copyOf(ffmpegStreamArgs);
        this.ffmpegVideoArgs = List.copyOf(ffmpegVideoArgs);
        this.internalData = internalData;
        this.initialized = initialized;
    }

    public Video(String url, String quality, String videoUrl, Headers headers,
                 List<Track> subtitleTracks, List<Track> audioTracks) {
        this(videoUrl == null ? "null" : videoUrl, quality, null, null, headers, false,
                subtitleTracks, audioTracks, List.of(), List.of(), List.of(), List.of(), "", false);
        videoPageUrl = url;
    }

    public Video(String url, String quality, String videoUrl, Headers headers,
                 List<Track> subtitleTracks, List<Track> audioTracks, int mask,
                 DefaultConstructorMarker marker) {
        this(url, quality, videoUrl,
                (mask & 8) == 0 ? headers : new Headers.Builder().build(),
                (mask & 16) == 0 ? subtitleTracks : List.of(),
                (mask & 32) == 0 ? audioTracks : List.of());
    }

    public Video(String url, String quality, String videoUrl, Uri uri, Headers headers) {
        this(url, quality, videoUrl, headers, List.of(), List.of());
    }

    public Video(String url, String quality, String videoUrl, Uri uri, Headers headers, int mask,
                 DefaultConstructorMarker marker) {
        this(url, quality, videoUrl, uri,
                (mask & 16) == 0 ? headers : new Headers.Builder().build());
    }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String value) { videoUrl = value; }
    public String getVideoTitle() { return videoTitle; }
    public String getQuality() { return videoTitle; }
    public String getUrl() { return videoPageUrl; }
    public Integer getResolution() { return resolution; }
    public Integer getBitrate() { return bitrate; }
    public Headers getHeaders() { return headers; }
    public boolean getPreferred() { return preferred; }
    public List<Track> getSubtitleTracks() { return subtitleTracks; }
    public List<Track> getAudioTracks() { return audioTracks; }
    public List<TimeStamp> getTimestamps() { return timestamps; }
    public List<?> getMpvArgs() { return mpvArgs; }
    public List<?> getFfmpegStreamArgs() { return ffmpegStreamArgs; }
    public List<?> getFfmpegVideoArgs() { return ffmpegVideoArgs; }
    public String getInternalData() { return internalData; }
    public boolean getInitialized() { return initialized; }
    public State getStatus() { return status; }
    public void setStatus(State value) { status = value; }

    public Video copy(String url, String quality, String resolvedVideoUrl, Headers newHeaders,
                      List<Track> newSubtitleTracks, List<Track> newAudioTracks) {
        return new Video(url, quality, resolvedVideoUrl, newHeaders, newSubtitleTracks, newAudioTracks);
    }

    public static Video copy$default(Video video, String url, String quality, String resolvedVideoUrl,
                                     Headers headers, List<Track> subtitleTracks, List<Track> audioTracks,
                                     int mask, Object marker) {
        return video.copy(
                (mask & 1) == 0 ? url : video.getUrl(),
                (mask & 2) == 0 ? quality : video.getQuality(),
                (mask & 4) == 0 ? resolvedVideoUrl : video.getVideoUrl(),
                (mask & 8) == 0 ? headers : video.getHeaders(),
                (mask & 16) == 0 ? subtitleTracks : video.getSubtitleTracks(),
                (mask & 32) == 0 ? audioTracks : video.getAudioTracks());
    }

    public String component1() { return getUrl(); }
    public String component2() { return getQuality(); }
    public String component3() { return getVideoUrl(); }
    public Headers component4() { return getHeaders(); }
    public List<Track> component5() { return getSubtitleTracks(); }
    public List<Track> component6() { return getAudioTracks(); }

    public enum State {
        QUEUE,
        LOAD_VIDEO,
        READY,
        ERROR
    }
}
