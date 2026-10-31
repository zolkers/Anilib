package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

public record Track(String url, String lang) {
    public String getUrl() {
        return url;
    }

    public String getLang() {
        return lang;
    }

    public String component1() {
        return url;
    }

    public String component2() {
        return lang;
    }

    public Track copy(String url, String lang) {
        return new Track(url, lang);
    }

    public static Track copy$default(
            Track source, String url, String lang, int mask, Object marker) {
        return source.copy(
                (mask & 1) == 0 ? url : source.url, (mask & 2) == 0 ? lang : source.lang);
    }
}
