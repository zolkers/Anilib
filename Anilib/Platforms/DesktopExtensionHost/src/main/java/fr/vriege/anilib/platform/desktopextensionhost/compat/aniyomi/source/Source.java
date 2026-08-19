package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source;

public interface Source {
    long getId();

    String getName();

    default String getLang() {
        return "";
    }
}
