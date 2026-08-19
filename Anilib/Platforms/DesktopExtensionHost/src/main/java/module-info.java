module fr.vriege.anilib.platform.desktopextensionhost {
    requires jdk.httpserver;
    requires java.net.http;
    requires java.xml;
    requires org.objectweb.asm;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.tree;

    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;
    exports fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source;
}
