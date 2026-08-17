module fr.vriege.anilib.framework.http.jdk.runtime {
    requires java.net.http;
    requires transitive fr.vriege.anilib.framework.http.api;

    exports fr.vriege.anilib.framework.http.jdk;
}
