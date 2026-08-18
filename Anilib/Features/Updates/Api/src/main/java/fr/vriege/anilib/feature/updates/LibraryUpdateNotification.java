package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Objects;

public record LibraryUpdateNotification(
        LibraryUpdateNotificationType type,
        String title,
        String message,
        int completed,
        int total) {
    public LibraryUpdateNotification {
        Objects.requireNonNull(type, "type must not be null");
        title = type == LibraryUpdateNotificationType.CLEAR_PROGRESS
                ? Objects.requireNonNull(title, "title must not be null")
                : Preconditions.requireNonBlank(title, "title");
        message = Objects.requireNonNull(message, "message must not be null");
        if (completed < 0 || total < 0 || completed > total) {
            throw new IllegalArgumentException("notification progress is invalid");
        }
    }
}
