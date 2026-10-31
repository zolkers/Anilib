package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;

public enum FetchType {
    Seasons,
    Episodes;

    public static EnumEntries<FetchType> getEntries() {
        return EnumEntriesKt.enumEntries(values());
    }
}
