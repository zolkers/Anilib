package fr.vriege.anilib.feature.localsource;

import java.util.List;

/** Secure reader for folder and ZIP/CBZ publications beneath one configured root. */
public interface LocalContentSource {
    List<LocalPublication> publications();

    List<LocalPage> pages(LocalPublicationId publicationId);

    byte[] read(LocalPage page);
}
