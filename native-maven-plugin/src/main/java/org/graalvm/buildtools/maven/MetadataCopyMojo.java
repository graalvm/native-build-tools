package org.graalvm.buildtools.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.graalvm.buildtools.maven.config.MetadataCopyConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "metadata-copy", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class MetadataCopyMojo extends AbstractMojo {

    @Parameter(alias = "metadataCopy")
    private MetadataCopyConfiguration metadataCopy;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        ArrayList<Path> sources = new ArrayList<>(Arrays.asList(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory())));
        ArrayList<Path> destinations = metadataCopy.getOutputDirectories();

        for (Path source: sources) {
            for (Path destination : destinations) {
                try {
                    Files.copy(source, destination);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }
}
