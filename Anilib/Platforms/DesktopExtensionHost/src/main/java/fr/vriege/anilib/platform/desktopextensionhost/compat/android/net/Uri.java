package fr.vriege.anilib.platform.desktopextensionhost.compat.android.net;

import java.net.URI;
import java.util.Objects;

public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static Uri parse(String value) { return new Uri(value); }
    public String getScheme() { return URI.create(value).getScheme(); }
    public String getHost() { return URI.create(value).getHost(); }
    public String getPath() { return URI.create(value).getPath(); }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object other) { return other instanceof Uri uri && value.equals(uri.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
