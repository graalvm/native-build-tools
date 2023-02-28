package org.graalvm.buildtools.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.graalvm.buildtools.utils.NativeImageUtils;

import java.io.File;
import java.util.List;

/**
 * Persists the arguments file to be used by the native-image command. This can be useful in situations where
 * Native Build Tools plugin is not available, for example, when running native-image in a Docker container.
 *
 * The path to the args file is stored in the project properties under the key {@code graalvm.native-image.args-file}.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 0.9.21
 */
@Mojo(name = WriteArgsFileMojo.NAME, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class WriteArgsFileMojo extends NativeCompileNoForkMojo {

    public static final String NAME = "write-args-file";
    public static final String PROPERTY_NAME = "graalvm.native-image.args-file";

    @Override
    public void execute() throws MojoExecutionException {
        List<String> args = getBuildArgs();
        List<String> conversionResult = NativeImageUtils.convertToArgsFile(args, outputDirectory.toPath());
        if (conversionResult.size() == 1) {
            String argsFileName = conversionResult.get(0).replace("@", "");
            getLog().info("Args file written to: " + argsFileName);
            File argsFile = new File(argsFileName);
            project.getProperties().setProperty(PROPERTY_NAME, argsFile.getAbsolutePath());
        } else {
            throw new MojoExecutionException("Error writing args file");
        }
    }
}
