/**
 * Dependency-free localization contracts for feature-owned user-interface
 * messages.
 *
 * <p>Each feature contributes one immutable
 * {@link fr.vriege.anilib.framework.localization.TranslationCatalog} identified
 * by its component owner. A
 * {@link fr.vriege.anilib.framework.localization.Translator} assembles the
 * selected catalogs explicitly and resolves stable keys, exact English
 * compatibility aliases, and numbered resource templates in stable order.</p>
 *
 * <p>The source message is always the fallback. Unsupported languages, absent
 * entries, empty catalog selections, and blank source strings therefore remain
 * safe and readable without a platform dictionary or global mutable registry.</p>
 */
package fr.vriege.anilib.framework.localization;
