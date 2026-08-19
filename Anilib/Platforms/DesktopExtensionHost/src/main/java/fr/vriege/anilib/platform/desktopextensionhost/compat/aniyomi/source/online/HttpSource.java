package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.CatalogueSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public abstract class HttpSource implements CatalogueSource {
    private final NetworkHelper network = NetworkHelper.shared();
    private Long id;
    private Headers headers;

    public HttpSource() {
    }

    public abstract String getBaseUrl();

    protected final NetworkHelper getNetwork() { return network; }

    public final Headers getHeaders() {
        if (headers == null) {
            headers = headersBuilder().build();
        }
        return headers;
    }

    public OkHttpClient getClient() { return network.getClient(); }

    protected Headers.Builder headersBuilder() {
        return new Headers.Builder().add("User-Agent", network.defaultUserAgentProvider());
    }

    public int getVersionId() {
        return 1;
    }

    @Override
    public long getId() {
        if (id == null) {
            id = generateId(getName(), getLang(), getVersionId());
        }
        return id;
    }

    protected long generateId(String name, String language, int versionId) {
        String key = name.toLowerCase(Locale.ROOT) + '/' + language + '/' + versionId;
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 digest is unavailable", exception);
        }
    }

    @Override
    public String toString() {
        return getName() + " (" + getLang().toUpperCase(Locale.ROOT) + ')';
    }
}
