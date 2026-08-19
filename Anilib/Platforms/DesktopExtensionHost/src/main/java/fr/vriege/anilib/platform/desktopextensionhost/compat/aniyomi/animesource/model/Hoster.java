package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.List;

public class Hoster {
    public static final String NO_HOSTER_LIST = "no_hoster_list";
    private final String hosterUrl;
    private final String hosterName;
    private final List<Video> videoList;
    private final String internalData;
    private final boolean lazy;
    private State status = State.IDLE;

    public Hoster(String hosterUrl, String hosterName, List<Video> videoList,
                  String internalData, boolean lazy) {
        this.hosterUrl = hosterUrl;
        this.hosterName = hosterName;
        this.videoList = videoList == null ? null : List.copyOf(videoList);
        this.internalData = internalData;
        this.lazy = lazy;
    }

    public String getHosterUrl() { return hosterUrl; }
    public String getHosterName() { return hosterName; }
    public List<Video> getVideoList() { return videoList; }
    public String getInternalData() { return internalData; }
    public boolean getLazy() { return lazy; }
    public State getStatus() { return status; }
    public void setStatus(State value) { status = value; }

    public enum State {
        IDLE,
        LOADING,
        READY,
        ERROR
    }
}
