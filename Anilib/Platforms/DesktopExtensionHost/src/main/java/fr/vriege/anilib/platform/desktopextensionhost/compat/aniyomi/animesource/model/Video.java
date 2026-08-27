package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import java.util.List;
import java.util.Objects;
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
        this.subtitleTracks = immutable(subtitleTracks);
        this.audioTracks = immutable(audioTracks);
        this.timestamps = immutable(timestamps);
        this.mpvArgs = immutable(mpvArgs);
        this.ffmpegStreamArgs = immutable(ffmpegStreamArgs);
        this.ffmpegVideoArgs = immutable(ffmpegVideoArgs);
        this.internalData = Objects.requireNonNullElse(internalData, "");
        this.initialized = initialized;
    }

    public Video(String videoUrl, String videoTitle, Integer resolution, Integer bitrate, Headers headers,
                 boolean preferred, List<Track> subtitleTracks, List<Track> audioTracks,
                 List<TimeStamp> timestamps, List<?> mpvArgs, List<?> ffmpegStreamArgs,
                 List<?> ffmpegVideoArgs, String internalData, boolean initialized, int mask,
                 DefaultConstructorMarker marker) {
        this(
                (mask & 1) == 0 ? videoUrl : "",
                (mask & 2) == 0 ? videoTitle : "",
                (mask & 4) == 0 ? resolution : null,
                (mask & 8) == 0 ? bitrate : null,
                (mask & 16) == 0 ? headers : null,
                (mask & 32) == 0 && preferred,
                (mask & 64) == 0 ? subtitleTracks : List.of(),
                (mask & 128) == 0 ? audioTracks : List.of(),
                (mask & 256) == 0 ? timestamps : List.of(),
                (mask & 512) == 0 ? mpvArgs : List.of(),
                (mask & 1024) == 0 ? ffmpegStreamArgs : List.of(),
                (mask & 2048) == 0 ? ffmpegVideoArgs : List.of(),
                (mask & 4096) == 0 ? internalData : "",
                (mask & 8192) == 0 && initialized);
    }

    public Video() {
        this("", "", null, null, null, false, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), "", false);
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

    public Video copy(String newVideoUrl, String newVideoTitle, Integer newResolution, Integer newBitrate,
                      Headers newHeaders, boolean newPreferred, List<Track> newSubtitleTracks,
                      List<Track> newAudioTracks, List<TimeStamp> newTimestamps, List<?> newMpvArgs,
                      List<?> newFfmpegStreamArgs, List<?> newFfmpegVideoArgs, String newInternalData,
                      boolean newInitialized) {
        return new Video(newVideoUrl, newVideoTitle, newResolution, newBitrate, newHeaders, newPreferred,
                newSubtitleTracks, newAudioTracks, newTimestamps, newMpvArgs, newFfmpegStreamArgs,
                newFfmpegVideoArgs, newInternalData, newInitialized);
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

    public static Video copy$default(
            Video video, String videoUrl, String videoTitle, Integer resolution, Integer bitrate, Headers headers,
            boolean preferred, List<Track> subtitleTracks, List<Track> audioTracks,
            List<TimeStamp> timestamps, List<?> mpvArgs, List<?> ffmpegStreamArgs, List<?> ffmpegVideoArgs,
            String internalData, boolean initialized, int mask, Object marker) {
        return video.copy(
                (mask & 1) == 0 ? videoUrl : video.getVideoUrl(),
                (mask & 2) == 0 ? videoTitle : video.getVideoTitle(),
                (mask & 4) == 0 ? resolution : video.getResolution(),
                (mask & 8) == 0 ? bitrate : video.getBitrate(),
                (mask & 16) == 0 ? headers : video.getHeaders(),
                (mask & 32) == 0 ? preferred : video.getPreferred(),
                (mask & 64) == 0 ? subtitleTracks : video.getSubtitleTracks(),
                (mask & 128) == 0 ? audioTracks : video.getAudioTracks(),
                (mask & 256) == 0 ? timestamps : video.getTimestamps(),
                (mask & 512) == 0 ? mpvArgs : video.getMpvArgs(),
                (mask & 1024) == 0 ? ffmpegStreamArgs : video.getFfmpegStreamArgs(),
                (mask & 2048) == 0 ? ffmpegVideoArgs : video.getFfmpegVideoArgs(),
                (mask & 4096) == 0 ? internalData : video.getInternalData(),
                (mask & 8192) == 0 ? initialized : video.getInitialized());
    }

    public String component1() { return getUrl(); }
    public String component2() { return getQuality(); }
    public String component3() { return getVideoUrl(); }
    public Headers component4() { return getHeaders(); }
    public List<Track> component5() { return getSubtitleTracks(); }
    public List<Track> component6() { return getAudioTracks(); }

    public List<Track> component7() { return getSubtitleTracks(); }
    public List<Track> component8() { return getAudioTracks(); }
    public List<TimeStamp> component9() { return getTimestamps(); }
    public List<?> component10() { return getMpvArgs(); }
    public List<?> component11() { return getFfmpegStreamArgs(); }
    public List<?> component12() { return getFfmpegVideoArgs(); }
    public String component13() { return getInternalData(); }
    public boolean component14() { return getInitialized(); }

    private static <T> List<T> immutable(List<? extends T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public enum State {
        QUEUE,
        LOAD_VIDEO,
        READY,
        ERROR
    }
}
