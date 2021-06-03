/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.buildtools.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class containing various gradle related methods.
 */
@SuppressWarnings("unused")
public class GradleUtils {

    public static final List<String> DEPENDENT_CONFIGURATIONS = Arrays.asList(
            JavaPlugin.API_CONFIGURATION_NAME,
            JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME,
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME);

    static Logger logger;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasTestClasses(Project project) {
        FileCollection testClasspath = getSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs();
        return testClasspath.getFiles().stream().anyMatch(File::exists);
    }

    public static SourceSet getSourceSet(Project project, String sourceSetName) {
        SourceSetContainer sourceSetContainer = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
        return sourceSetContainer.findByName(sourceSetName);
    }

    public static FileCollection getClassPath(Project project) {
        FileCollection main = getClassPath(project, SourceSet.MAIN_SOURCE_SET_NAME);
        FileCollection test = getClassPath(project, SourceSet.TEST_SOURCE_SET_NAME);
        return main.plus(test);
    }

    @SuppressWarnings("SameParameterValue")
    public static FileCollection getClassPath(Project project, String sourceSetName) {
        return getSourceSet(project, sourceSetName).getRuntimeClasspath();
    }

    public static void initLogger(Project project) {
        logger = project.getLogger();
    }

    public static void log(String s) {
        logger.info("[native-image-plugin] {}", s);
    }

    public static void error(String s) {
        logger.error("[native-image-plugin] {}", s);
    }

    public static Path getTargetDir(Project project) {
        return project.getBuildDir().toPath().resolve("native-image");
    }
}
