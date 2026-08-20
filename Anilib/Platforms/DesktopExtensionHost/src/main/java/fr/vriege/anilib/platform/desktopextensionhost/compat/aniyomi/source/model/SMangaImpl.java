package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.Map;
import kotlinx.serialization.json.JsonObject;
import java.util.Objects;

public final class SMangaImpl implements SManga {
    private static final long serialVersionUID = 1L;
    public String url;
    public String title;
    private String artist;
    private String author;
    private String description;
    private String genre;
    private int status;
    private String thumbnailUrl;
    private UpdateStrategy updateStrategy = UpdateStrategy.ALWAYS_UPDATE;
    private boolean initialized;
    private transient JsonObject memo;

    public SMangaImpl() {
        memo = new JsonObject(Map.of());
    }

    @Override public String getUrl() { return require(url, "url"); }
    @Override public void setUrl(String value) { url = required(value); }
    @Override public String getTitle() { return require(title, "title"); }
    @Override public void setTitle(String value) { title = required(value); }
    @Override public String getArtist() { return artist; }
    @Override public void setArtist(String value) { artist = value; }
    @Override public String getAuthor() { return author; }
    @Override public void setAuthor(String value) { author = value; }
    @Override public String getDescription() { return description; }
    @Override public void setDescription(String value) { description = value; }
    @Override public String getGenre() { return genre; }
    @Override public void setGenre(String value) { genre = value; }
    @Override public int getStatus() { return status; }
    @Override public void setStatus(int value) { status = value; }
    @Override public String getThumbnail_url() { return thumbnailUrl; }
    @Override public void setThumbnail_url(String value) { thumbnailUrl = value; }
    @Override public UpdateStrategy getUpdate_strategy() { return updateStrategy; }
    @Override public void setUpdate_strategy(UpdateStrategy value) { updateStrategy = required(value); }
    @Override public boolean getInitialized() { return initialized; }
    @Override public void setInitialized(boolean value) { initialized = value; }
    @Override public JsonObject getMemo() { return memo; }
    @Override public void setMemo(JsonObject value) { memo = required(value); }

    private static <T> T required(T value) {
        return Objects.requireNonNull(value, "value");
    }

    private static String require(String value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " has not been initialized");
        }
        return value;
    }
}
