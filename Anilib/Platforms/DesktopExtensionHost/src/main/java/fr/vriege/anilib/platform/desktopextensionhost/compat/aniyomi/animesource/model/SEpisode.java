package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.io.Serializable;
import kotlinx.serialization.json.JsonObject;

public interface SEpisode extends Serializable {
    Companion Companion = new Companion();

    String getUrl();
    void setUrl(String url);
    String getName();
    void setName(String name);
    float getEpisode_number();
    void setEpisode_number(float episodeNumber);
    boolean getFillermark();
    void setFillermark(boolean fillermark);
    String getScanlator();
    void setScanlator(String scanlator);
    long getDate_upload();
    void setDate_upload(long dateUpload);
    String getSummary();
    void setSummary(String summary);
    String getPreview_url();
    void setPreview_url(String previewUrl);
    JsonObject getMemo();
    void setMemo(JsonObject memo);

    default void copyFrom(SEpisode episode) {
        setUrl(episode.getUrl());
        setName(episode.getName());
        setEpisode_number(episode.getEpisode_number());
        setFillermark(episode.getFillermark());
        setScanlator(episode.getScanlator());
        setDate_upload(episode.getDate_upload());
        setSummary(episode.getSummary());
        setPreview_url(episode.getPreview_url());
        setMemo(episode.getMemo());
    }

    final class Companion {
        private Companion() {
        }

        public SEpisode create() {
            return new SEpisodeImpl();
        }
    }
}
