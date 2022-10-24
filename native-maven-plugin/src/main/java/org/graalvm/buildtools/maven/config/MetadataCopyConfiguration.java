package org.graalvm.buildtools.maven.config;

import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetadataCopyConfiguration {

    @Parameter
    private List<Path> outputDirectories;

    @Parameter(defaultValue = "${project.basedir}/src/main/resources/META-INF/native-image")
    private Path outputDirectory;

    public ArrayList<Path> getOutputDirectories() {
        ArrayList<Path> tmp = new ArrayList<>(outputDirectories);
        tmp.add(outputDirectory);
        return tmp;
    }

    public void setOutputDirectories(List<Path> outputDirectories) {
        this.outputDirectories = outputDirectories;
    }
}
