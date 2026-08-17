package fr.vriege.anilib.foundation.component;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Observable metadata for one installable Anilib component. */
public record ComponentDescriptor(ComponentId id, String displayName, String version) {
    public ComponentDescriptor {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(displayName, "displayName");
        Preconditions.requireNonBlank(version, "version");
    }

    public static ComponentDescriptor of(String id, String displayName, String version) {
        return new ComponentDescriptor(ComponentId.of(id), displayName, version);
    }
}

