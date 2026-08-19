/**
 * Defines feature-owned, independently versioned backup section contracts.
 *
 * <p>The module separates section encoding from archive coordination and
 * provides a prepare/commit/rollback protocol for cross-feature restoration.</p>
 */
module fr.vriege.anilib.framework.backup.api {
    requires transitive fr.vriege.anilib.foundation;

    exports fr.vriege.anilib.framework.backup;
}
