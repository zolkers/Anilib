package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

public record Track(String url, String lang) {
    public String getUrl() { return url; }
    public String getLang() { return lang; }
    public String component1() { return url; }
    public String component2() { return lang; }
}
