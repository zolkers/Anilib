package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderReadStateStore;

import java.util.Objects;
import java.util.Collection;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class PrivacyAwareReaderReadStateStore implements ReaderReadStateStore {
    private final ReaderReadStateStore delegate;
    private final BooleanSupplier persistenceAllowed;

    public PrivacyAwareReaderReadStateStore(
            ReaderReadStateStore delegate,
            BooleanSupplier persistenceAllowed) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.persistenceAllowed = Objects.requireNonNull(
                persistenceAllowed,
                "persistenceAllowed must not be null");
    }

    @Override
    public Set<String> readContentIds(LibraryItemId libraryItemId) {
        return delegate.readContentIds(libraryItemId);
    }

    @Override
    public void setRead(LibraryItemId libraryItemId, String contentId, boolean read) {
        if (persistenceAllowed.getAsBoolean()) {
            delegate.setRead(libraryItemId, contentId, read);
        }
    }

    @Override
    public void setRead(LibraryItemId libraryItemId, Collection<String> contentIds, boolean read) {
        if (persistenceAllowed.getAsBoolean()) {
            delegate.setRead(libraryItemId, contentIds, read);
        }
    }
}
