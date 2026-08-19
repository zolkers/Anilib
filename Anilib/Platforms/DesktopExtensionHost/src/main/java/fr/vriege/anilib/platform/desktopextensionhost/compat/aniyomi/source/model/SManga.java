package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import kotlinx.serialization.json.JsonObject;

public interface SManga extends Serializable {
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
    String getArtist();
    void setArtist(String artist);
    String getAuthor();
    void setAuthor(String author);
    String getDescription();
    void setDescription(String description);
    String getGenre();
    void setGenre(String genre);
    int getStatus();
    void setStatus(int status);
    String getThumbnail_url();
    void setThumbnail_url(String thumbnailUrl);
    UpdateStrategy getUpdate_strategy();
    void setUpdate_strategy(UpdateStrategy updateStrategy);
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

    default SManga copy() {
        SManga copy = Companion.create();
        copy.setUrl(getUrl());
        copy.setTitle(getTitle());
        copy.setArtist(getArtist());
        copy.setAuthor(getAuthor());
        copy.setDescription(getDescription());
        copy.setGenre(getGenre());
        copy.setStatus(getStatus());
        copy.setThumbnail_url(getThumbnail_url());
        copy.setUpdate_strategy(getUpdate_strategy());
        copy.setInitialized(getInitialized());
        copy.setMemo(getMemo());
        return copy;
    }

    final class Companion {
        private Companion() {
        }

        public SManga create() {
            return new SMangaImpl();
        }
    }
}
