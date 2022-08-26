package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

public class AgentConfiguration {
    @Parameter(property = "enabled", defaultValue = "false")
    protected boolean enabled;

    @Parameter(property = "defaultMode", defaultValue = "standard")
    protected String defaultMode;

    @Parameter(property = "disabledPhases")
    protected List<String> disabledPhases;

    @Parameter(property = "modes")
    protected AgentModeConfiguration modes;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }

    public List<String> getDisabledPhases() {
        return disabledPhases;
    }

    public void setDisabledPhases(List<String> disabledPhases) {
        this.disabledPhases = disabledPhases;
    }

    public AgentModeConfiguration getModes() {
        return modes;
    }

    public void setModes(AgentModeConfiguration modes) {
        this.modes = modes;
    }
}
