package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;

public interface AnimeSource {
    long getId();

    String getName();

    default String getLang() {
        return "";
    }

    boolean getSupportsLatest();
}
