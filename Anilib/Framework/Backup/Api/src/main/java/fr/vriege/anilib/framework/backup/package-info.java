/**
 * Contracts for independently versioned, feature-owned backup sections.
 *
 * <p>A feature exposes one
 * {@link fr.vriege.anilib.framework.backup.BackupSectionCodec}. The codec owns
 * its binary format and stable
 * {@link fr.vriege.anilib.framework.backup.BackupSectionId}; an outer backup
 * coordinator owns archive framing, checksums, section order, and filesystem
 * durability.</p>
 *
 * <p>Restoration has three phases:</p>
 *
 * <ol>
 *   <li>inspect and prepare every known section without mutation;</li>
 *   <li>commit each prepared section in order; and</li>
 *   <li>if a later commit fails, roll back earlier commits in reverse order.</li>
 * </ol>
 *
 * <p>This boundary lets removable features validate and migrate their own state
 * without coupling the coordinator to feature storage formats. Unknown future
 * sections remain skippable, while malformed or unsupported known sections
 * fail before mutation.</p>
 */
package fr.vriege.anilib.framework.backup;
