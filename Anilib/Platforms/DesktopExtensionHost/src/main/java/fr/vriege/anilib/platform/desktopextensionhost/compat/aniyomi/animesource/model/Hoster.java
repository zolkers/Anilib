package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import kotlin.jvm.internal.DefaultConstructorMarker;

import kotlinx.serialization.json.JsonObject;

import java.util.List;
import java.util.Map;

public class Hoster {
    public static final String NO_HOSTER_LIST = "no_hoster_list";
    public static final Companion Companion = new Companion();
    private final String hosterUrl;
    private final String hosterName;
    private final List<Video> videoList;
    private final String internalData;
    private final boolean lazy;
    private final JsonObject memo;
    private State status = State.IDLE;

    public Hoster(
            String hosterUrl,
            String hosterName,
            List<Video> videoList,
            String internalData,
            boolean lazy) {
        this(hosterUrl, hosterName, videoList, internalData, lazy, new JsonObject(Map.of()));
    }

    public Hoster(
            String hosterUrl,
            String hosterName,
            List<Video> videoList,
            String internalData,
            boolean lazy,
            JsonObject memo) {
        this.hosterUrl = hosterUrl;
        this.hosterName = hosterName;
        this.videoList = videoList == null ? null : List.copyOf(videoList);
        this.internalData = internalData;
        this.lazy = lazy;
        this.memo = memo;
    }

    public Hoster(
            String url,
            String name,
            List<Video> videos,
            String data,
            boolean lazy,
            int mask,
            DefaultConstructorMarker marker) {
        this(
                (mask & 1) != 0 ? "" : url,
                (mask & 2) != 0 ? "" : name,
                (mask & 4) != 0 ? null : videos,
                (mask & 8) != 0 ? "" : data,
                (mask & 16) == 0 && lazy);
    }

    public Hoster(
            String url,
            String name,
            List<Video> videos,
            String data,
            boolean lazy,
            JsonObject memo,
            int mask,
            DefaultConstructorMarker marker) {
        this(
                (mask & 1) != 0 ? "" : url,
                (mask & 2) != 0 ? "" : name,
                (mask & 4) != 0 ? null : videos,
                (mask & 8) != 0 ? "" : data,
                (mask & 16) == 0 && lazy,
                (mask & 32) != 0 ? new JsonObject(Map.of()) : memo);
    }

    public Hoster() {
        this("", "", null, "", false);
    }

    public Hoster copy(String url, String name, List<Video> videos, String data, boolean lazy) {
        return new Hoster(url, name, videos, data, lazy, memo);
    }

    public Hoster copy(
            String url,
            String name,
            List<Video> videos,
            String data,
            boolean lazy,
            JsonObject memo) {
        return new Hoster(url, name, videos, data, lazy, memo);
    }

    public static Hoster copy$default(
            Hoster source,
            String url,
            String name,
            List<Video> videos,
            String data,
            boolean lazy,
            int mask,
            Object marker) {
        return copy$default(source, url, name, videos, data, lazy, source.memo, mask, marker);
    }

    public static Hoster copy$default(
            Hoster source,
            String url,
            String name,
            List<Video> videos,
            String data,
            boolean lazy,
            JsonObject memo,
            int mask,
            Object marker) {
        return source.copy(
                (mask & 1) != 0 ? source.hosterUrl : url,
                (mask & 2) != 0 ? source.hosterName : name,
                (mask & 4) != 0 ? source.videoList : videos,
                (mask & 8) != 0 ? source.internalData : data,
                (mask & 16) != 0 ? source.lazy : lazy,
                (mask & 32) != 0 ? source.memo : memo);
    }

    public String getHosterUrl() {
        return hosterUrl;
    }

    public String getHosterName() {
        return hosterName;
    }

    public List<Video> getVideoList() {
        return videoList;
    }

    public String getInternalData() {
        return internalData;
    }

    public boolean getLazy() {
        return lazy;
    }

    public JsonObject getMemo() {
        return memo;
    }

    public State getStatus() {
        return status;
    }

    public void setStatus(State value) {
        status = value;
    }

    public static final class Companion {
        private Companion() {}

        public Companion(DefaultConstructorMarker marker) {}

        public List<Hoster> toHosterList(List<Video> videos) {
            return List.of(new Hoster("", NO_HOSTER_LIST, videos, "", false));
        }
    }

    public enum State {
        IDLE,
        LOADING,
        READY,
        ERROR
    }
}
