package fr.vriege.anilib.platform.desktopengine;

import fr.vriege.anilib.platform.desktopengine.server.DesktopEngineServer;

import java.util.concurrent.CountDownLatch;

public final class DesktopEngineMain {
    private DesktopEngineMain() {
    }

    public static void main(String[] arguments) throws InterruptedException {
        DesktopEngineArguments configuration = DesktopEngineArguments.parse(arguments);
        DesktopEngineServer server = DesktopEngineServer.open(
                configuration.address(),
                configuration.port(),
                configuration.dataDirectory());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "anilib-desktop-engine-shutdown"));
        server.start();
        System.out.println("Anilib desktop engine listening on http://127.0.0.1:" + server.port());
        new CountDownLatch(1).await();
    }
}
