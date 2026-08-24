package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.List;
import java.util.Objects;

record VideoDownloadPlan(VideoDownloadMetadata metadata, List<SourcePageResource> resources) {
    VideoDownloadPlan {
        metadata = Objects.requireNonNull(metadata, "metadata");
        resources = List.copyOf(resources);
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("video download plan must contain resources");
        }
    }
}
