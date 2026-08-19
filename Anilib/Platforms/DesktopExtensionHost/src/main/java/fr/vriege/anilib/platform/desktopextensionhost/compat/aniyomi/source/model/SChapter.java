package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.io.Serializable;
import kotlinx.serialization.json.JsonObject;

public interface SChapter extends Serializable {
    Companion Companion = new Companion();

    String getUrl();
    void setUrl(String url);
    String getName();
    void setName(String name);
    long getDate_upload();
    void setDate_upload(long dateUpload);
    float getChapter_number();
    void setChapter_number(float chapterNumber);
    String getScanlator();
    void setScanlator(String scanlator);
    JsonObject getMemo();
    void setMemo(JsonObject memo);

    default void copyFrom(SChapter chapter) {
        setUrl(chapter.getUrl());
        setName(chapter.getName());
        setDate_upload(chapter.getDate_upload());
        setChapter_number(chapter.getChapter_number());
        setScanlator(chapter.getScanlator());
        setMemo(chapter.getMemo());
    }

    final class Companion {
        private Companion() {
        }

        public SChapter create() {
            return new SChapterImpl();
        }
    }
}
