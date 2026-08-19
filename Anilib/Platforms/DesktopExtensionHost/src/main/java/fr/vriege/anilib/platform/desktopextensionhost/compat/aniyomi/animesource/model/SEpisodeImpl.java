package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.Map;
import java.util.Objects;
import kotlinx.serialization.json.JsonObject;

public final class SEpisodeImpl implements SEpisode {
    private static final long serialVersionUID = 1L;
    public String url;
    public String name;
    private float episodeNumber = -1.0f;
    private boolean fillermark;
    private String scanlator;
    private long dateUpload;
    private String summary;
    private String previewUrl;
    private transient JsonObject memo;

    public SEpisodeImpl() {
        memo = new JsonObject(Map.of());
    }

    @Override public String getUrl() { return require(url, "url"); }
    @Override public void setUrl(String value) { url = Objects.requireNonNull(value, "value"); }
    @Override public String getName() { return require(name, "name"); }
    @Override public void setName(String value) { name = Objects.requireNonNull(value, "value"); }
    @Override public float getEpisode_number() { return episodeNumber; }
    @Override public void setEpisode_number(float value) { episodeNumber = value; }
    @Override public boolean getFillermark() { return fillermark; }
    @Override public void setFillermark(boolean value) { fillermark = value; }
    @Override public String getScanlator() { return scanlator; }
    @Override public void setScanlator(String value) { scanlator = value; }
    @Override public long getDate_upload() { return dateUpload; }
    @Override public void setDate_upload(long value) { dateUpload = value; }
    @Override public String getSummary() { return summary; }
    @Override public void setSummary(String value) { summary = value; }
    @Override public String getPreview_url() { return previewUrl; }
    @Override public void setPreview_url(String value) { previewUrl = value; }
    @Override public JsonObject getMemo() { return memo; }
    @Override public void setMemo(JsonObject value) { memo = Objects.requireNonNull(value, "value"); }

    private static String require(String value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " has not been initialized");
        }
        return value;
    }
}
