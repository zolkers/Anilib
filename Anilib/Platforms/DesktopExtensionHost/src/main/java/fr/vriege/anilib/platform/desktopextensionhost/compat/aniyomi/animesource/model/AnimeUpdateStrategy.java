package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;

public enum AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE;

    public static EnumEntries<AnimeUpdateStrategy> getEntries() {
        return EnumEntriesKt.enumEntries(values());
    }
}
