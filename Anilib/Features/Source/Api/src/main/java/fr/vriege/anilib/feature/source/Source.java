package fr.vriege.anilib.feature.source;

/** Base contract implemented by every local or remote source. */
public interface Source {
    SourceDescriptor descriptor();
}
