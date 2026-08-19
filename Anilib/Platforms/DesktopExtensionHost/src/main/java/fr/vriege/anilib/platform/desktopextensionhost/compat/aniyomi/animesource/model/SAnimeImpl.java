package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.Map;
import java.util.Objects;
import kotlinx.serialization.json.JsonObject;

public final class SAnimeImpl implements SAnime {
    private static final long serialVersionUID = 1L;
    public String url;
    public String title;
    private String thumbnailUrl;
    private String backgroundUrl;
    private String artist;
    private String author;
    private int status;
    private String description;
    private String genre;
    private boolean initialized;
    private AnimeUpdateStrategy updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE;
    private FetchType fetchType = FetchType.Episodes;
    private double seasonNumber = -1.0d;
    private transient JsonObject memo;

    public SAnimeImpl() {
        memo = new JsonObject(Map.of());
    }

    @Override public String getUrl() { return require(url, "url"); }
    @Override public void setUrl(String value) { url = Objects.requireNonNull(value, "value"); }
    @Override public String getTitle() { return require(title, "title"); }
    @Override public void setTitle(String value) { title = Objects.requireNonNull(value, "value"); }
    @Override public String getThumbnail_url() { return thumbnailUrl; }
    @Override public void setThumbnail_url(String value) { thumbnailUrl = value; }
    @Override public String getBackground_url() { return backgroundUrl; }
    @Override public void setBackground_url(String value) { backgroundUrl = value; }
    @Override public String getArtist() { return artist; }
    @Override public void setArtist(String value) { artist = value; }
    @Override public String getAuthor() { return author; }
    @Override public void setAuthor(String value) { author = value; }
    @Override public int getStatus() { return status; }
    @Override public void setStatus(int value) { status = value; }
    @Override public String getDescription() { return description; }
    @Override public void setDescription(String value) { description = value; }
    @Override public String getGenre() { return genre; }
    @Override public void setGenre(String value) { genre = value; }
    @Override public boolean getInitialized() { return initialized; }
    @Override public void setInitialized(boolean value) { initialized = value; }
    @Override public AnimeUpdateStrategy getUpdate_strategy() { return updateStrategy; }
    @Override
    public void setUpdate_strategy(AnimeUpdateStrategy value) {
        updateStrategy = Objects.requireNonNull(value, "value");
    }
    @Override public FetchType getFetch_type() { return fetchType; }
    @Override public void setFetch_type(FetchType value) { fetchType = Objects.requireNonNull(value, "value"); }
    @Override public double getSeason_number() { return seasonNumber; }
    @Override public void setSeason_number(double value) { seasonNumber = value; }
    @Override public JsonObject getMemo() { return memo; }
    @Override public void setMemo(JsonObject value) { memo = Objects.requireNonNull(value, "value"); }

    private static String require(String value, String property) {
        if (value == null) {
            throw new IllegalStateException(property + " has not been initialized");
        }
        return value;
    }
}
