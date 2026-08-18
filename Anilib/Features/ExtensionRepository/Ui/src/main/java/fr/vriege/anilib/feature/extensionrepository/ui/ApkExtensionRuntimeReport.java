package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record ApkExtensionRuntimeReport(
        String packageName,
        ApkExtensionRuntimeState state,
        List<String> missingHostClasses,
        Optional<String> trustedCertificateSha256,
        Optional<String> activationFailure) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ApkExtensionRuntimeReport {
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
        activationFailure = Preconditions.requireNonNull(activationFailure, "activationFailure")
                .map(value -> Preconditions.requireNonBlank(value, "activationFailure"));
        trustedCertificateSha256.ifPresent(value -> {
            if (!SHA_256.matcher(value).matches()) {
                throw new IllegalArgumentException("trustedCertificateSha256 must be 64 lowercase hex characters");
            }
        });
        if (state == ApkExtensionRuntimeState.HOST_ABI_MISSING && missingHostClasses.isEmpty()) {
            throw new IllegalArgumentException("HOST_ABI_MISSING requires at least one missing class");
        }
        if (state != ApkExtensionRuntimeState.HOST_ABI_MISSING && !missingHostClasses.isEmpty()) {
            throw new IllegalArgumentException("Only HOST_ABI_MISSING may list missing classes");
        }
        boolean trustExpected = state == ApkExtensionRuntimeState.HOST_ABI_MISSING
                || state == ApkExtensionRuntimeState.HOST_ABI_AVAILABLE
                || state == ApkExtensionRuntimeState.ACTIVATION_FAILED
                || state == ApkExtensionRuntimeState.ACTIVE;
        if (trustExpected != trustedCertificateSha256.isPresent()) {
            throw new IllegalArgumentException("Runtime trust state and certificate do not match");
        }
        if ((state == ApkExtensionRuntimeState.ACTIVATION_FAILED) != activationFailure.isPresent()) {
            throw new IllegalArgumentException("Only ACTIVATION_FAILED must retain an activation failure");
        }
    }

    public static ApkExtensionRuntimeReport unsupported(String packageName) {
        return new ApkExtensionRuntimeReport(
                packageName,
                ApkExtensionRuntimeState.UNSUPPORTED_PLATFORM,
                List.of(),
                Optional.empty(),
                Optional.empty());
    }

    public static ApkExtensionRuntimeReport active(String packageName, String certificateSha256) {
        return new ApkExtensionRuntimeReport(
                packageName,
                ApkExtensionRuntimeState.ACTIVE,
                List.of(),
                Optional.of(certificateSha256),
                Optional.empty());
    }

    public static ApkExtensionRuntimeReport activationFailed(
            String packageName,
            String certificateSha256,
            String failure) {
        return new ApkExtensionRuntimeReport(
                packageName,
                ApkExtensionRuntimeState.ACTIVATION_FAILED,
                List.of(),
                Optional.of(certificateSha256),
                Optional.of(failure));
    }
}
