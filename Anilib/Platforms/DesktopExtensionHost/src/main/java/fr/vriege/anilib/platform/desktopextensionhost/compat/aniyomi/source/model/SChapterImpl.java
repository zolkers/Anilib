package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.Map;
import java.util.Objects;
import kotlinx.serialization.json.JsonObject;

public final class SChapterImpl implements SChapter {
    private static final long serialVersionUID = 1L;
    public String url;
    public String name;
    private long dateUpload;
    private float chapterNumber = -1.0f;
    private String scanlator;
    private transient JsonObject memo;

    public SChapterImpl() {
        memo = new JsonObject(Map.of());
    }

    @Override public String getUrl() { return require(url, "url"); }
    @Override public void setUrl(String value) { url = Objects.requireNonNull(value, "value"); }
    @Override public String getName() { return require(name, "name"); }
    @Override public void setName(String value) { name = Objects.requireNonNull(value, "value"); }
    @Override public long getDate_upload() { return dateUpload; }
    @Override public void setDate_upload(long value) { dateUpload = value; }
    @Override public float getChapter_number() { return chapterNumber; }
    @Override public void setChapter_number(float value) { chapterNumber = value; }
    @Override public String getScanlator() { return scanlator; }
    @Override public void setScanlator(String value) { scanlator = value; }
    @Override public JsonObject getMemo() { return memo; }
    @Override public void setMemo(JsonObject value) { memo = Objects.requireNonNull(value, "value"); }

    private static String require(String value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " has not been initialized");
        }
        return value;
    }
}
