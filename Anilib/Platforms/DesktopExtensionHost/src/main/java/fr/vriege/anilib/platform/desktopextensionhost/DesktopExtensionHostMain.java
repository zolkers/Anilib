package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.platform.desktopextensionhost.server.DesktopExtensionHostServer;

import java.util.concurrent.CountDownLatch;

public final class DesktopExtensionHostMain {
    private DesktopExtensionHostMain() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        DesktopExtensionHostArguments configuration = DesktopExtensionHostArguments.parse(arguments);
        DesktopExtensionHostServer server = DesktopExtensionHostServer.open(
                configuration.address(),
                configuration.port(),
                configuration.dataDirectory());
        Runtime.getRuntime().addShutdownHook(ManagedExecutors.thread(
                "anilib-desktop-extension-host-shutdown",
                server::close));
        server.start();
        System.out.println("Anilib desktop engine listening on http://127.0.0.1:" + server.port());
        new CountDownLatch(1).await();
    }
}
