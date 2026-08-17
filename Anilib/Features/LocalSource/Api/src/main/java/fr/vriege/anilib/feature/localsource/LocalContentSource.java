package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.feature.source.Source;

import java.util.List;

/** Secure reader for folder and ZIP/CBZ publications beneath one configured root. */
public interface LocalContentSource extends Source {
    List<LocalPublication> publications();

    List<LocalPage> pages(LocalPublicationId publicationId);

    byte[] read(LocalPage page);
}
