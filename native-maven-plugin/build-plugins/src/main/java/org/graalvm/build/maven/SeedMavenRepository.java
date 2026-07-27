/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and in any patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and the
 * Larger Work(s), and to make, use, sell, offer for sale, import, export, have
 * made, and have sold the Software and the Larger Work(s), and to sublicense
 * the foregoing rights on either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.build.maven;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the reusable Maven seed without replacing a previous valid seed until Maven succeeds.
 * §E2E-functional-tests.4
 */
public abstract class SeedMavenRepository extends MavenTask {
    public SeedMavenRepository() {
        getOutputs().upToDateWhen(task -> hasValidSeed());
    }

    @Input
    public abstract MapProperty<String, String> getSeedProperties();

    @Override
    protected void prepareArguments(List<String> arguments) {
        File staging = stagingDirectory();
        getFileSystemOperations().delete(spec -> spec.delete(staging));
        arguments.add("-Dproject.build.directory=" + new File(staging, "target").getAbsolutePath());
        arguments.add("-Dmaven.repo.local=" + new File(staging, "repository").getAbsolutePath());
    }

    @Override
    protected void extractOutput(File projectDirectory, File outputDirectory) {
        Path stagingRoot = stagingDirectory().toPath();
        Path stagingRepository = stagingRoot.resolve("repository");
        getFileSystemOperations().copy(spec ->
                spec.from(stagingRoot.resolve("target")).into(stagingRepository));
        try {
            MavenRepositorySeedState.write(stagingRepository, currentInputKey());
            replace(stagingRepository, outputDirectory.toPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not publish the seeded Maven repository", ex);
        }
    }

    String currentInputKey() throws IOException {
        Map<String, String> values = new LinkedHashMap<>(getSeedProperties().get());
        values.put("arguments", String.join("\n", getArguments().get()));
        values.put("offline", getOffline().get().toString());
        values.put("updateSnapshots", getUpdateSnapshots().get().toString());
        Map<String, Path> files = new LinkedHashMap<>();
        files.put("projectDirectory", getProjectDirectory().getAsFile().get().toPath());
        files.put("settingsFile", getSettingsFile().getAsFile().get().toPath());
        int index = 0;
        for (File file : getMavenEmbedderClasspath().getFiles()) {
            files.put("mavenEmbedderClasspath." + index++, file.toPath());
        }
        return MavenRepositorySeedState.inputKey(values, files);
    }

    private boolean hasValidSeed() {
        try {
            return MavenRepositorySeedState.isValid(
                    getOutputDirectory().getAsFile().get().toPath(),
                    currentInputKey());
        } catch (IOException ex) {
            return false;
        }
    }

    private File stagingDirectory() {
        return new File(getTemporaryDir(), "staging");
    }

    private void replace(Path staging, Path target) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".previous");
        getFileSystemOperations().delete(spec -> spec.delete(backup));
        if (Files.exists(target)) {
            move(target, backup);
        }
        try {
            move(staging, target);
            getFileSystemOperations().delete(spec -> spec.delete(backup));
        } catch (IOException ex) {
            if (Files.exists(backup) && !Files.exists(target)) {
                move(backup, target);
            }
            throw ex;
        }
    }

    private static void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }
}
