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

    /**
     * Represents a required schema by base name with an exact required major version.
     */
    private record RequiredSchema(String baseName, int requiredMajorVersion) {
    }

    private static final String REACHABILITY_METADATA_SCHEMA = "reachability-metadata-schema";
    private static final String REACHABILITY_METADATA_SCHEMA_PATH = "lib/svm/schemas/reachability-metadata-schema.json";

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
    public static void validateSchemas(Path repoRoot) {
        Path schemasDir = repoRoot.resolve("schemas");

        if (!Files.isDirectory(schemasDir)) {
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " does not contain a 'schemas' directory. "
                + "Requires GraalVM Reachability Metadata 0.3.33 or newer which packages the required schemas.";
            throw new IllegalStateException(message);
        }

        int requiredSchemasFound = 0;
        List<String> missing = new ArrayList<>();
        List<String> metadataTooOld = new ArrayList<>();
        List<String> toolsTooOld = new ArrayList<>();
        String prefix = repoRoot.relativize(schemasDir).toString();

        // Count files but explicitly ignore the optional reachability-metadata-schema for "exact count" purposes
        try (DirectoryStream<Path> allFiles = Files.newDirectoryStream(schemasDir)) {
            for (Path f : allFiles) {
                if (!f.getFileName().toString().startsWith(REACHABILITY_METADATA_SCHEMA)) {
                    requiredSchemasFound++;
                }
            }
        } catch (IOException ignored) {}

        if (requiredSchemasFound > REQUIRED_SCHEMAS.length) {
            String message = "The configured GraalVM reachability metadata repository at "
                    + repoRoot.toAbsolutePath()
                    + " contains more schema files than supported by this version of Native Build Tools. "
                    + "Found " + requiredSchemasFound + " files under 'schemas' (excluding optional reachability-metadata-schema) but exactly "
                    + REQUIRED_SCHEMAS.length + " are supported. "
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
    }

    /**
     * Performs optional reachability-metadata-schema cross-validation between the used reachability metadata repository
     * and the provided GraalVM installation. This does not affect the mandatory schema checks and is skipped entirely
     * if both sides don't provide the schema.
     */
    public static void validateReachabilityMetadataSchema(Path repoRoot, int majorJDKVersion) {
        Path schemasDir = repoRoot.resolve("schemas");
        // Optional reachability-metadata schema cross-validation with GRAALVM_HOME
        // Determine presence by existence of the schema files only (no version parsing here)

        boolean repoHas = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(schemasDir, REACHABILITY_METADATA_SCHEMA + "-v*.json")) {
            for (Path ignored : stream) {
                repoHas = true;
                break;
            }
        } catch (IOException ignored) {}

        // Locate GraalVM home and check only for schema file existence
        String graalvmHomeLocation = System.getenv("GRAALVM_HOME");
        if (graalvmHomeLocation == null) {
            graalvmHomeLocation = System.getenv("JAVA_HOME");
        }
        boolean graalHas = false;
        if (graalvmHomeLocation != null) {
            try {
                Path graalSchema = Path.of(graalvmHomeLocation).resolve(REACHABILITY_METADATA_SCHEMA_PATH);
                graalHas = Files.isRegularFile(graalSchema);
            } catch (Exception ignored) {}
        }

        // Apply the four-case logic based solely on existence
        if (!repoHas && !graalHas) {
            // both missing -> no-op
            return;
        } else if (repoHas && !graalHas) {
            // repo has, GraalVM missing
            String message = "The configured GraalVM reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " provides a reachability-metadata schema, but your GraalVM installation at "
                + graalvmHomeLocation
                + " does not. Please update your GraalVM installation to a newer version.";
            if (majorJDKVersion < 21) {
                message += " Update to the latest GraalVM 21.x or 25.x.";
            } else {
                message += " Update to the latest available release in your line (21.x or 25.x).";
            }
            throw new IllegalStateException(message);
        } else if (!repoHas && graalHas) {
            // GraalVM has, repo missing
            String message = "Your GraalVM installation at "
                + graalvmHomeLocation
                + " provides a reachability-metadata schema, but the configured reachability metadata repository at "
                + repoRoot.toAbsolutePath()
                + " does not. Please update your reachability metadata repository to a newer version.";
            throw new IllegalStateException(message);
        } else {
            // both present -> perform version comparison using helper
            compareReachabilityMetadataSchemaVersions(repoRoot, graalvmHomeLocation);
        }
    }
    private record SemVer(int major, int minor, int patch) {}

    private static SemVer parseRepoReachabilitySchemaVersion(Path schemasDir) {
        Pattern reachabilityPattern = Pattern.compile(Pattern.quote(REACHABILITY_METADATA_SCHEMA) + "-v(\\d+)\\.(\\d+)\\.(\\d+)\\.json");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(schemasDir, REACHABILITY_METADATA_SCHEMA + "-v*.json")) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                Matcher m = reachabilityPattern.matcher(name);
                if (m.matches()) {
                    int maj = safeParseInt(m.group(1));
                    int min = safeParseInt(m.group(2));
                    int pat = safeParseInt(m.group(3));
                    return new SemVer(maj, min, pat);
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static SemVer parseGraalReachabilitySchemaVersion(Path graalHome) {
        try {
            Path graalSchema = graalHome.resolve(REACHABILITY_METADATA_SCHEMA_PATH);
            if (Files.isRegularFile(graalSchema)) {
                String content = Files.readString(graalSchema);
                Matcher vm = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+)\\.(\\d+)\\.(\\d+)\"").matcher(content);
                if (vm.find()) {
                    int maj = safeParseInt(vm.group(1));
                    int min = safeParseInt(vm.group(2));
                    int pat = safeParseInt(vm.group(3));
                    return new SemVer(maj, min, pat);
                }
            }
        } catch (IOException ignored) {}
        return null;
    }
    private static void compareReachabilityMetadataSchemaVersions(Path repoRoot, String graalvmHomeLocation) {
        Path schemasDir = repoRoot.resolve("schemas");
        SemVer repoV = parseRepoReachabilitySchemaVersion(schemasDir);
        if (graalvmHomeLocation == null) {
            return;
        }
        Path graalHome = Path.of(graalvmHomeLocation);
        SemVer graalV = parseGraalReachabilitySchemaVersion(graalHome);

        // If parsing failed for either side, mimic previous behavior by skipping comparison
        if (repoV == null || graalV == null) {
            return;
        }

        int cmp = Integer.compare(repoV.major, graalV.major);
        if (cmp == 0) {
            cmp = Integer.compare(repoV.minor, graalV.minor);
        }
        if (cmp == 0) {
            cmp = Integer.compare(repoV.patch, graalV.patch);
        }
        if (cmp != 0) {
            String message = "Detected reachability-metadata schema version mismatch. Repository v"
              + repoV.major +  "." + repoV.minor + "." + repoV.patch
              + " vs GraalVM v"
              + graalV.major + "." + graalV.minor + "." + graalV.patch
              + ". ";
            if (cmp < 0) {
                // repo lower
                message += "Please update the reachability metadata repository.";
            } else {
                // graal lower
                message += "Please update your GraalVM installation.";
            }
            throw new IllegalStateException(message);
        }
    }
}
