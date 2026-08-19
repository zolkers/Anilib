package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import kotlinx.serialization.json.JsonObject;

public interface SAnime extends Serializable {
    Companion Companion = new Companion();
    int UNKNOWN = 0;
    int ONGOING = 1;
    int COMPLETED = 2;
    int LICENSED = 3;
    int PUBLISHING_FINISHED = 4;
    int CANCELLED = 5;
    int ON_HIATUS = 6;

    String getUrl();
    void setUrl(String url);
    String getTitle();
    void setTitle(String title);
    String getThumbnail_url();
    void setThumbnail_url(String thumbnailUrl);
    String getBackground_url();
    void setBackground_url(String backgroundUrl);
    String getArtist();
    void setArtist(String artist);
    String getAuthor();
    void setAuthor(String author);
    int getStatus();
    void setStatus(int status);
    String getDescription();
    void setDescription(String description);
    String getGenre();
    void setGenre(String genre);
    AnimeUpdateStrategy getUpdate_strategy();
    void setUpdate_strategy(AnimeUpdateStrategy updateStrategy);
    FetchType getFetch_type();
    void setFetch_type(FetchType fetchType);
    double getSeason_number();
    void setSeason_number(double seasonNumber);
    boolean getInitialized();
    void setInitialized(boolean initialized);
    JsonObject getMemo();
    void setMemo(JsonObject memo);

    default List<String> getGenres() {
        String genre = getGenre();
        if (genre == null || genre.isBlank()) {
            return List.of();
        }
        return Arrays.stream(genre.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    default SAnime copy() {
        SAnime copy = Companion.create();
        copy.setUrl(getUrl());
        copy.setTitle(getTitle());
        copy.setThumbnail_url(getThumbnail_url());
        copy.setBackground_url(getBackground_url());
        copy.setArtist(getArtist());
        copy.setAuthor(getAuthor());
        copy.setStatus(getStatus());
        copy.setDescription(getDescription());
        copy.setGenre(getGenre());
        copy.setUpdate_strategy(getUpdate_strategy());
        copy.setFetch_type(getFetch_type());
        copy.setSeason_number(getSeason_number());
        copy.setInitialized(getInitialized());
        copy.setMemo(getMemo());
        return copy;
    }

    final class Companion {
        private Companion() {
        }

        public SAnime create() {
            return new SAnimeImpl();
        }
    }
}
