module fr.vriege.anilib.platform.desktopextensionhost {
    requires fr.vriege.anilib.framework.concurrent.runtime;
    requires jdk.httpserver;
    requires java.net.http;
    requires java.xml;
    requires org.objectweb.asm;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.tree;
    requires transitive kotlinx.serialization.json;
    requires transitive kotlinx.coroutines.core;
    requires transitive okhttp3;
    requires transitive org.jsoup;
    requires transitive rxjava;

    exports fr.vriege.anilib.platform.desktopextensionhost;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.app;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.net;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.text;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.util;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.view;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.android.widget;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.injekt;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.injekt.api;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.quickjs;
}
