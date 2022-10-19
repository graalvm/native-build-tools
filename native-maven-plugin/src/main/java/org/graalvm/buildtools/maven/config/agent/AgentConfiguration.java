package org.graalvm.buildtools.maven.config.agent;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.HashMap;
import java.util.LinkedList;

public class AgentConfiguration {



    private boolean isEnabled;
    private LinkedList<AgentMode> modes;
    private AgentMode defaultMode;
    private LinkedList<String> disabledPhases;
    private HashMap<String, String> commonOptions;
    private AgentMetadataCopy metadataCopy;

    public AgentConfiguration() {
        isEnabled = false;
        modes = new LinkedList<>();
        defaultMode = null;
        disabledPhases = new LinkedList<>();
        commonOptions = new HashMap<>();
        metadataCopy = null;
    }

    public void readAgentConfiguration(Xpp3Dom root) {

    }

}
