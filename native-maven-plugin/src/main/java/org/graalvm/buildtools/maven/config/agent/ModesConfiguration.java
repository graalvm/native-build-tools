package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.Properties;

public class ModesConfiguration {

    @Parameter
    private Properties conditional;

    @Parameter
    private String direct;

    public Properties getConditional() {
        return conditional;
    }

    public String getDirect() {
        return direct;
    }

    public void setConditional(Properties conditional) {
        this.conditional = conditional;
    }

    public void setDirect(String direct) {
        this.direct = direct;
    }
}
