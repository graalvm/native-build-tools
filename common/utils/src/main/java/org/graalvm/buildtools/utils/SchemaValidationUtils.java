package org.graalvm.buildtools.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;

/**
 * Utility methods for validating that required schema files at or above specific minimal versions
 * are present in a GraalVM reachability metadata repository.
 */
public final class SchemaValidationUtils {

    private SchemaValidationUtils() {
        // no-op
    }

    /**
     * Minimal required schema baselines. Any schema file with a version equal to or newer than
     * the minimal version for the given base name satisfies the requirement.
     * For example, if minimal is 1.0.0 then 1.0.1 or 1.1.0 will pass.
     */
    private static final RequiredSchema[] REQUIRED_SCHEMAS = new RequiredSchema[] {
        new RequiredSchema("library-and-framework-list-schema", "1.0.0"),
        new RequiredSchema("metadata-library-index-schema", "1.0.0"),
        new RequiredSchema("metadata-root-index-schema", "1.0.0")
    };

    /**
     * Represents a required schema by base name with a minimal semantic version (major.minor.patch).
     */
    private static final class RequiredSchema {
        final String baseName;
        final int[] minVersion;

        private RequiredSchema(String baseName, String minVersion) {
            this.baseName = baseName;
            this.minVersion = parseVersion(minVersion);
        }
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int major = parts.length > 0 ? safeParseInt(parts[0]) : 0;
        int minor = parts.length > 1 ? safeParseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? safeParseInt(parts[2]) : 0;
        return new int[] { major, minor, patch };
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(a[i], b[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static String versionToString(int[] v) {
        return v[0] + "." + v[1] + "." + v[2];
    }

    /**
     * Validates that the repository at the given root provides required schemas
     * at or above specific minimal versions and enforces a strict schema count.
     *
     * For each required schema base name, this method ensures there is a file
     * named {@code baseName-vMAJOR.MINOR.PATCH.json} with a semantic version greater than
     * or equal to the minimal baseline (for example, {@code baseName-v1.0.0.json}, {@code v1.0.1.json}, {@code v1.1.0.json}).
     *
     * This method also validates that the total number of files in the schemas directory
     * does not exceed the number of supported schemas to ensure compatibility.
     *
     * If the schemas directory is missing, if any minimal-version requirement is not satisfied,
     * or if the directory contains more files than are supported by this version of
     * Native Build Tools, this method throws an {@link IllegalStateException} with a detailed message.
     *
     * @param repoRoot the root path of the exploded repository
     */
    public static void validateSchemas(Path repoRoot) {
        Path schemasDir = repoRoot.resolve("schemas");

        if (!Files.isDirectory(schemasDir)) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " does not contain a 'schemas' directory. "
                + "Requires GraalVM Reachability Metadata 0.3.33 or newer which packages the required schemas.";
            throw new IllegalStateException(message);
        }

        int totalFilesFound = 0;
        List<String> missing = new ArrayList<>();
        String prefix = repoRoot.relativize(schemasDir).toString();

        try (DirectoryStream<Path> allFiles = Files.newDirectoryStream(schemasDir)) {
            for (Path ignored : allFiles) {
                totalFilesFound++;
            }
        } catch (IOException ignored) {}

        if (totalFilesFound > REQUIRED_SCHEMAS.length) {
            String message = "The configured GraalVM reachability metadata repository at "
                    + repoRoot.toAbsolutePath()
                    + " contains more schema files than supported by this version of Native Build Tools. "
                    + "Found " + totalFilesFound + " files under 'schemas' but exactly " + REQUIRED_SCHEMAS.length + " are supported. "
                    + "Please update your Native Build Tools to a newer version which supports the newer schemas.";
            throw new IllegalStateException(message);
        }

        for (RequiredSchema required : REQUIRED_SCHEMAS) {
            boolean satisfied = false;
            Pattern pattern = Pattern.compile(Pattern.quote(required.baseName) + "-v(\\d+)\\.(\\d+)\\.(\\d+)\\.json");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(schemasDir, required.baseName + "-v*.json")) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    Matcher m = pattern.matcher(name);
                    if (m.matches()) {
                        int[] found = new int[] {
                            Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3))
                        };
                        if (compareVersions(found, required.minVersion) >= 0) {
                            satisfied = true;
                            break;
                        }
                    }
                }
            } catch (IOException ignored) {
                // ignore and treat as unsatisfied; validation will fail below
            }
            if (!satisfied) {
                missing.add(prefix + "/" + required.baseName + "-v" + versionToString(required.minVersion) + "+.json");
            }
        }

        if (!missing.isEmpty()) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " is missing required schema files (requires GraalVM Reachability Metadata 0.3.33 or newer): "
                + String.join(", ", missing)
                + ". If you customized metadataRepository (uri/url/version/localPath), please point it to a 0.3.33+ release.";
            throw new IllegalStateException(message);
        }
    }
}
