package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;

public enum ChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other;

    public static EnumEntries<ChapterType> getEntries() {
        return EnumEntriesKt.enumEntries(values());
    }
}
