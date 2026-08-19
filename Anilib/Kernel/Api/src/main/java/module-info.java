/**
 * Defines Anilib's public plugin graph and lifecycle API.
 *
 * <p>The module exports the kernel contracts and transitively exposes the
 * foundation identities used by manifests and running-product inventories. It
 * contains no plugin runtime implementation; products select a kernel runtime
 * explicitly.</p>
 */
module fr.vriege.anilib.kernel.api {
    requires transitive fr.vriege.anilib.foundation;

    exports fr.vriege.anilib.kernel;
}
