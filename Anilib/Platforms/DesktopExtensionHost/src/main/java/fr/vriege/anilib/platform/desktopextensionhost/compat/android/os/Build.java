package fr.vriege.anilib.platform.desktopextensionhost.compat.android.os;

public final class Build {
    public static final String BRAND = "Anilib";
    public static final String DEVICE = "desktop";
    public static final String MANUFACTURER = "Anilib";
    public static final String MODEL = "Desktop";
    public static final String PRODUCT = "anilib_desktop";
    public static final String[] SUPPORTED_ABIS = {System.getProperty("os.arch", "unknown")};

    private Build() {
    }

    public static final class VERSION {
        public static final String CODENAME = "REL";
        public static final String INCREMENTAL = "1";
        public static final String RELEASE = "14";
        public static final String SECURITY_PATCH = "2026-08-01";
        public static final int SDK_INT = 34;

        private VERSION() {
        }
    }

    public static final class VERSION_CODES {
        public static final int N = 24;
        public static final int O = 26;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;

        private VERSION_CODES() {
        }
    }
}
