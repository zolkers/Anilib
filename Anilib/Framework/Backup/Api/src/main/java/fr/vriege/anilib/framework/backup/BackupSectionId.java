package fr.vriege.anilib.framework.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

/**
 * The stable, format-level identity of one independently versioned backup
 * section.
 *
 * <p>Section identifiers are persisted in backup archives and must therefore
 * remain stable across application versions. Valid identifiers begin with a
 * lowercase ASCII letter and continue with lowercase letters, digits, dots, or
 * hyphens. Natural ordering is lexicographic by identifier value.</p>
 *
 * @param value the stable lowercase identifier
 */
public record BackupSectionId(String value) implements Comparable<BackupSectionId> {
    /**
     * Creates and validates a section identifier.
     *
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or does not
     *                                  have the required identifier form
     */
    public BackupSectionId {
        value = Preconditions.requireNonBlank(value, "value");
        if (!value.matches("[a-z][a-z0-9.-]*")) {
            throw new IllegalArgumentException("value must be a stable lowercase identifier");
        }
    }

    /**
     * Creates a section identifier from its textual value.
     *
     * @param value the stable lowercase identifier
     * @return the validated identifier
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or invalid
     */
    public static BackupSectionId of(String value) {
        return new BackupSectionId(value);
    }

    /**
     * Compares this identifier lexicographically with another identifier.
     *
     * @param other the identifier to compare with
     * @return a negative integer, zero, or a positive integer as this identifier
     *         is less than, equal to, or greater than {@code other}
     * @throws NullPointerException if {@code other} is {@code null}
     */
    @Override
    public int compareTo(BackupSectionId other) {
        return value.compareTo(other.value);
    }

    /**
     * Returns the persistent textual identifier.
     *
     * @return {@link #value()}
     */
    @Override
    public String toString() {
        return value;
    }
}
