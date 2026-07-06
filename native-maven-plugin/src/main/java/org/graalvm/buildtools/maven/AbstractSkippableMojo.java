/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */

package org.graalvm.buildtools.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractSkippableMojo extends AbstractMojo {

    // A root plugin configuration skips every goal before its goal-specific work begins. §FS-config-model.6.
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping native Maven plugin goal (parameter 'skip' is true).");
            return;
        }
        executeInternal();
    }

    protected abstract void executeInternal() throws MojoExecutionException, MojoFailureException;
}
