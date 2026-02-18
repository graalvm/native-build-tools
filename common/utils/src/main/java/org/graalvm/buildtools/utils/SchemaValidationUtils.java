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
 * Utility methods for validating that required schema files with an exact major version
 * are present in a GraalVM reachability metadata repository.
 * Schema compatibility is determined by the major version only:
 * - If Build Tools requires a higher major than what the repository provides, validation fails
 * and the user must update the reachability metadata repository.
 * - If the repository provides a higher major than supported by Build Tools, validation fails
 * and the user must update Native Build Tools.
 * The check also ensures the schema directory is present and contains a valid set of files.
 */
public final class SchemaValidationUtils {
    /**
     * Required schema descriptors. The major version must match exactly between
     * Native Build Tools and the reachability metadata repository.
     */
    private static final RequiredSchema[] REQUIRED_SCHEMAS = new RequiredSchema[] {
        new RequiredSchema("library-and-framework-list-schema", 1),
        new RequiredSchema("metadata-library-index-schema", 2),
    };
    public static final String REACHABILITY_METADATA_SCHEMA_JSON_NAME = "reachability-metadata-schema.json";

    /**
     * Represents a required schema by base name with an exact required major version.
     */
    private record RequiredSchema(String baseName, int requiredMajorVersion) {
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Validates that the repository at the given root provides required schemas
     * whose major versions match exactly the ones supported by this Build Tools version,
     * and enforces a strict schema count.
     *
     * For each required schema base name, this method ensures there is exactly one file
     * named {@code baseName-vMAJOR.MINOR.PATCH.json} whose MAJOR equals the Build Tools
     * required MAJOR. If the repository uses an older MAJOR, users must update the
     * reachability metadata repository. If it uses a newer MAJOR, users must update
     * Native Build Tools.
     *
     * This method also validates that the total number of files in the schemas directory
     * does not exceed the number of supported schemas to ensure compatibility.
     *
     * If the schemas directory is missing, if any major-version requirement is not satisfied,
     * or if the directory contains more files than are supported by this version of
     * Native Build Tools, this method throws an {@link IllegalStateException} with a detailed message.
     *
     * @param repoRoot the root path of the exploded repository
     */
    public static String validateSchemas(Path repoRoot) {
        Path schemasDir = repoRoot.resolve("schemas");

        if (!Files.isDirectory(schemasDir)) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " does not contain a 'schemas' directory. "
                + "Requires GraalVM Reachability Metadata 0.3.33 or newer which packages the required schemas.";
            throw new IllegalStateException(message);
        }

        int totalFilesFound = 0;
        // Will try to extract the version from the descriptor file if present
        String repositorySchemaVersion = null;
        List<String> missing = new ArrayList<>();
        List<String> metadataTooOld = new ArrayList<>();
        List<String> toolsTooOld = new ArrayList<>();
        String prefix = repoRoot.relativize(schemasDir).toString();
        try (DirectoryStream<Path> allFiles = Files.newDirectoryStream(schemasDir)) {
            for (Path entry : allFiles) {
                String fileName = entry.getFileName().toString();
                if (REACHABILITY_METADATA_SCHEMA_JSON_NAME.equals(fileName)) {
                    // Do not count this descriptor file in the schema files total,
                    // but try to extract the repository schema version from it.
                    try {
                        String content = Files.readString(entry);
                        // Try common keys that may hold the version string
                        Pattern vpat = Pattern.compile("\"(?:schemaVersion|version)\"\\s*:\\s*\"(\\d+\\.\\d+\\.\\d+)\"");
                        Matcher vm = vpat.matcher(content);
                        if (vm.find()) {
                            repositorySchemaVersion = vm.group(1);
                        }
                    } catch (IOException ignored) {
                        // Best effort: ignore failures reading or parsing this optional descriptor
                    }
                    continue;
                }
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
            Integer foundMajor = null;
            Pattern pattern = Pattern.compile(Pattern.quote(required.baseName) + "-v(\\d+)\\.(\\d+)\\.(\\d+)\\.json");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(schemasDir, required.baseName + "-v*.json")) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    Matcher m = pattern.matcher(name);
                    if (m.matches()) {
                        foundMajor = safeParseInt(m.group(1));
                        break; // there should be at most one per schema; stop at first match
                    }
                }
            } catch (IOException ignored) {
                // ignore and treat as unsatisfied; validation will fail below
            }

            if (foundMajor == null) {
                missing.add(prefix + "/" + required.baseName + "-v" + required.requiredMajorVersion + ".*.*.json");
            } else if (required.requiredMajorVersion > foundMajor) {
                metadataTooOld.add(prefix + "/" + required.baseName + ": required major v" + required.requiredMajorVersion + ", found v" + foundMajor);
            } else if (required.requiredMajorVersion < foundMajor) {
                toolsTooOld.add(prefix + "/" + required.baseName + ": required major v" + required.requiredMajorVersion + ", found v" + foundMajor);
            }
        }

        if (!missing.isEmpty()) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " is missing required schema files (exact major version required): "
                + String.join(", ", missing)
                + ". If you customized metadataRepository (uri/url/version/localPath), please point it to the latest official release.";
            throw new IllegalStateException(message);
        }

        if (!metadataTooOld.isEmpty()) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " provides schema files with an older major version than required by this version of Native Build Tools: "
                + String.join("; ", metadataTooOld)
                + ". Please update your reachability metadata repository.";
            throw new IllegalStateException(message);
        }

        if (!toolsTooOld.isEmpty()) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " provides schema files with a newer major version than supported by this version of Native Build Tools: "
                + String.join("; ", toolsTooOld)
                + ". Please update your Native Build Tools to a newer version.";
            throw new IllegalStateException(message);
        }
        return repositorySchemaVersion;
    }
}
