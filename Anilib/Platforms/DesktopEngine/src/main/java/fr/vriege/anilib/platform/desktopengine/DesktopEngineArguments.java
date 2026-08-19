package fr.vriege.anilib.platform.desktopengine;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

record DesktopEngineArguments(InetAddress address, int port, Path dataDirectory) {
    static DesktopEngineArguments parse(String[] arguments) {
        Map<String, String> values = pairs(arguments);
        String host = values.getOrDefault("--host", "127.0.0.1");
        int port;
        try {
            port = Integer.parseInt(values.getOrDefault("--port", "0"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--port must be an integer", exception);
        }
        String data = values.get("--data");
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("--data is required");
        }
        try {
            return new DesktopEngineArguments(InetAddress.getByName(host), port, Path.of(data));
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("--host is invalid", exception);
        }
    }

    private static Map<String, String> pairs(String[] arguments) {
        if (arguments.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be supplied as --name value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index += 2) {
            String name = arguments[index];
            if (!name.startsWith("--") || values.putIfAbsent(name, arguments[index + 1]) != null) {
                throw new IllegalArgumentException("Invalid or duplicate argument: " + name);
            }
        }
        return Map.copyOf(values);
    }
}
