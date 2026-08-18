package fr.vriege.anilib.feature.covercache;

import java.util.Arrays;
import java.util.Objects;

public final class DecodedImage {
    private final int width;
    private final int height;
    private final int[] argbPixels;

    public DecodedImage(int width, int height, int[] argbPixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        this.argbPixels = Objects.requireNonNull(argbPixels, "argbPixels must not be null").clone();
        long expectedPixels = (long) width * height;
        if (expectedPixels != argbPixels.length) {
            throw new IllegalArgumentException("pixel count does not match image dimensions");
        }
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int argbAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("pixel coordinates are outside the image");
        }
        return argbPixels[y * width + x];
    }

    public int[] argbPixels() {
        return argbPixels.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof DecodedImage other)) {
            return false;
        }
        return width == other.width
                && height == other.height
                && Arrays.equals(argbPixels, other.argbPixels);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(width);
        result = 31 * result + Integer.hashCode(height);
        return 31 * result + Arrays.hashCode(argbPixels);
    }

    @Override
    public String toString() {
        return "DecodedImage[width=" + width + ", height=" + height + ']';
    }
}
