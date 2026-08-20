package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.util.Objects;

public final class ExtensionOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Code code;
    private final String operation;
    private final String packageName;
    private final long sourceId;
    private final String sourceUrl;

    ExtensionOperationException(
            Code code,
            String operation,
            String packageName,
            long sourceId,
            String sourceUrl,
            Throwable cause) {
        super(message(code), cause);
        this.code = Objects.requireNonNull(code, "code");
        this.operation = requireText(operation, "operation");
        this.packageName = requireText(packageName, "packageName");
        this.sourceId = sourceId;
        this.sourceUrl = Objects.requireNonNullElse(sourceUrl, "");
    }

    public Code code() {
        return code;
    }

    public String operation() {
        return operation;
    }

    public String packageName() {
        return packageName;
    }

    public long sourceId() {
        return sourceId;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    private static String message(Code code) {
        return switch (code) {
            case UNSUPPORTED_CAPABILITY -> "The extension does not support this operation on desktop";
            case REMOTE_HTTP_FAILURE -> "The source website could not complete the request";
            case PARSE_FAILURE -> "The extension could not read the source response";
            case ABI_FAILURE -> "The extension is not compatible with the desktop host";
            case HOST_BUSY -> "The desktop extension host is busy";
            case OPERATION_SUPERSEDED -> "The extension operation was replaced by a newer request";
            case OPERATION_TIMEOUT -> "The extension operation timed out";
            case INTERNAL_HOST_FAILURE -> "The desktop extension host could not complete the request";
        };
    }

    private static String requireText(String value, String label) {
        String result = Objects.requireNonNull(value, label).strip();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return result;
    }

    public enum Code {
        UNSUPPORTED_CAPABILITY("unsupported_capability", 501),
        REMOTE_HTTP_FAILURE("remote_http_failure", 502),
        PARSE_FAILURE("parse_failure", 502),
        ABI_FAILURE("abi_failure", 422),
        HOST_BUSY("host_busy", 503),
        OPERATION_SUPERSEDED("operation_superseded", 409),
        OPERATION_TIMEOUT("operation_timeout", 504),
        INTERNAL_HOST_FAILURE("internal_host_failure", 500);

        private final String protocolValue;
        private final int statusCode;

        Code(String protocolValue, int statusCode) {
            this.protocolValue = protocolValue;
            this.statusCode = statusCode;
        }

        public String protocolValue() {
            return protocolValue;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
