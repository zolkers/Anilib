package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import java.io.IOException;

public class HttpException extends IOException {
    private static final long serialVersionUID = 1L;
    private final int code;

    public HttpException(int code) {
        super("HTTP " + code);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
