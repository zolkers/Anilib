package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online;

import kotlin.jvm.internal.DefaultConstructorMarker;

public abstract class UriType {
    private UriType() {}

    public UriType(DefaultConstructorMarker marker) {}

    public static final class Anime extends UriType {
        public static final Anime INSTANCE = new Anime();

        private Anime() {}

        @Override
        public String toString() {
            return "Anime";
        }
    }

    public static final class Episode extends UriType {
        public static final Episode INSTANCE = new Episode();

        private Episode() {}

        @Override
        public String toString() {
            return "Episode";
        }
    }

    public static final class Unknown extends UriType {
        public static final Unknown INSTANCE = new Unknown();

        private Unknown() {}

        @Override
        public String toString() {
            return "Unknown";
        }
    }
}
