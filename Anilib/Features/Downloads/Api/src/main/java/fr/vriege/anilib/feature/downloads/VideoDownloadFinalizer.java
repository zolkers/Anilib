package fr.vriege.anilib.feature.downloads;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Platform-owned finalization hook for downloaded video content.
 *
 * <p>The download runtime deliberately knows nothing about native media tools. A platform can
 * provide an implementation that remuxes the already-downloaded input into the requested output.
 * Manga page downloads never invoke this contract.
 */
public interface VideoDownloadFinalizer {
    /**
     * Returns whether this platform can finalize video downloads.
     *
     * @return {@code true} when {@link #finalizeVideo(VideoFinalizationRequest, BooleanSupplier)}
     *         may be called
     */
    boolean available();

    /**
     * Produces the requested media file before returning.
     *
     * @param request trusted local input and output paths
     * @param cancelled cooperative cancellation signal
     */
    void finalizeVideo(VideoFinalizationRequest request, BooleanSupplier cancelled);

    /**
     * Returns a finalizer for platforms that retain the resumable native download representation.
     *
     * @return an unavailable finalizer
     */
    static VideoDownloadFinalizer unavailable() {
        return new VideoDownloadFinalizer() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public void finalizeVideo(VideoFinalizationRequest request, BooleanSupplier cancelled) {
                Objects.requireNonNull(request, "request must not be null");
                Objects.requireNonNull(cancelled, "cancelled must not be null");
                throw new DownloadException("Video finalization is not available on this platform");
            }
        };
    }

    /**
     * Immutable local remux request.
     *
     * @param input downloaded progressive media or offline HLS playlist
     * @param output final media file to create atomically
     */
    record VideoFinalizationRequest(Path input, Path output) {
        public VideoFinalizationRequest {
            input = normalize(input, "input");
            output = normalize(output, "output");
            if (input.equals(output) || !Objects.equals(input.getParent(), output.getParent())) {
                throw new IllegalArgumentException("Video finalization paths must be distinct siblings");
            }
        }

        private static Path normalize(Path path, String name) {
            Path normalized = Objects.requireNonNull(path, name + " must not be null")
                    .toAbsolutePath()
                    .normalize();
            if (normalized.getParent() == null) {
                throw new IllegalArgumentException(name + " must have a parent directory");
            }
            return normalized;
        }
    }
}
