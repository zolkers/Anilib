package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Trust and host-ABI preflight for one Android-visible Aniyomi extension. */
public record LegacyExtensionRuntimeReport(
        String packageName,
        LegacyExtensionRuntimeState state,
        List<String> missingHostClasses,
        Optional<String> trustedCertificateSha256) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public LegacyExtensionRuntimeReport {
        packageName = Preconditions.requireNonBlank(packageName, "packageName");
        state = Preconditions.requireNonNull(state, "state");
        missingHostClasses = Preconditions.requireNonNull(missingHostClasses, "missingHostClasses").stream()
                .map(value -> Preconditions.requireNonBlank(value, "missingHostClass"))
                .distinct()
                .sorted()
                .toList();
        trustedCertificateSha256 = Preconditions.requireNonNull(
                trustedCertificateSha256,
                "trustedCertificateSha256")
                .map(value -> Preconditions.requireNonBlank(value, "trustedCertificateSha256")
                        .toLowerCase(Locale.ROOT));
        trustedCertificateSha256.ifPresent(value -> {
            if (!SHA_256.matcher(value).matches()) {
                throw new IllegalArgumentException("trustedCertificateSha256 must be 64 lowercase hex characters");
            }
        });
        if (state == LegacyExtensionRuntimeState.HOST_ABI_MISSING && missingHostClasses.isEmpty()) {
            throw new IllegalArgumentException("HOST_ABI_MISSING requires at least one missing class");
        }
        if (state != LegacyExtensionRuntimeState.HOST_ABI_MISSING && !missingHostClasses.isEmpty()) {
            throw new IllegalArgumentException("Only HOST_ABI_MISSING may list missing classes");
        }
        boolean trustExpected = state == LegacyExtensionRuntimeState.HOST_ABI_MISSING
                || state == LegacyExtensionRuntimeState.HOST_ABI_AVAILABLE;
        if (trustExpected != trustedCertificateSha256.isPresent()) {
            throw new IllegalArgumentException("Runtime trust state and certificate do not match");
        }
    }

    public static LegacyExtensionRuntimeReport unsupported(String packageName) {
        return new LegacyExtensionRuntimeReport(
                packageName,
                LegacyExtensionRuntimeState.UNSUPPORTED_PLATFORM,
                List.of(),
                Optional.empty());
    }
}
