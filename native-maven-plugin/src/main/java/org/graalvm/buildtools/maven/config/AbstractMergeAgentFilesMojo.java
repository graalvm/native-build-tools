package org.graalvm.buildtools.maven.config;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;
import org.graalvm.buildtools.utils.NativeImageUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.graalvm.buildtools.utils.NativeImageUtils.nativeImageConfigureFileName;

public abstract class AbstractMergeAgentFilesMojo extends AbstractMojo {


    @Component
    protected Logger logger;

    protected File mergerExecutable;

    protected void tryInstallMergeExecutable(Path nativeImageExecutablePath) {
        File nativeImageExecutable = nativeImageExecutablePath.toAbsolutePath().toFile();
        File mergerExecutable = new File(nativeImageExecutable.getParentFile(), nativeImageConfigureFileName());
        if (!mergerExecutable.exists()) {
            getLog().info("Installing native image merger to " + mergerExecutable);
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().add("--macro:native-image-configure-launcher");
            processBuilder.directory(mergerExecutable.getParentFile());
            processBuilder.inheritIO();

            try {
                Process installProcess = processBuilder.start();
                if (installProcess.waitFor() != 0) {
                    getLog().warn("Installation of native image merging tool failed");
                }
                NativeImageUtils.maybeCreateConfigureUtilSymlink(mergerExecutable, nativeImageExecutablePath);
            } catch (IOException | InterruptedException e) {
                // ignore since we will handle that if the installer doesn't exist later
            }

        }
        this.mergerExecutable = mergerExecutable;
    }

    protected void invokeMerge(File mergerExecutable, List<File> inputDirectories, File outputDirectory) throws MojoExecutionException {
        if (!mergerExecutable.exists()) {
            getLog().warn("Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM.");
            return;
        }
        try {
            if (inputDirectories.isEmpty()) {
                getLog().warn("Skipping merging of agent files since there are no input directories.");
                return;
            }
            getLog().info("Merging agent " + inputDirectories.size() + " files into " + outputDirectory);
            List<String> args = new ArrayList<>(inputDirectories.size() + 2);
            args.add("generate");
            inputDirectories.stream()
                    .map(f -> "--input-dir=" + f.getAbsolutePath())
                    .forEach(args::add);
            args.add("--output-dir=" + outputDirectory.getAbsolutePath());
            ProcessBuilder processBuilder = new ProcessBuilder(mergerExecutable.toString());
            processBuilder.command().addAll(args);
            processBuilder.inheritIO();

            String commandString = String.join(" ", processBuilder.command());
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
            for (File inputDirectory : inputDirectories) {
                FileUtils.deleteDirectory(inputDirectory);
            }
            getLog().debug("Agent output: " + Arrays.toString(outputDirectory.listFiles()));
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Merging agent files with " + mergerExecutable + " failed", e);
        }
    }

}
