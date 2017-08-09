package com.microsoft.azure.containeragents.aci;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class AciContainerTemplate extends AbstractDescribableImpl<AciContainerTemplate> implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(AciContainerTemplate.class.getName());

    private static final long serialVersionUID = 640938461214718337L;

    private String name;

    private String label;

    private String image;

    private String osType;

    private String command;

    private String rootFs;

    private String location;

    private List<AciPort> ports;

    private String cpu;

    private String memory;


    @DataBoundConstructor
    public AciContainerTemplate(String name,
                                String label,
                                String osType,
                                String image,
                                String command,
                                String rootFs,
                                List<AciPort> ports,
                                String cpu,
                                String memory) {
        this.name = name;
        this.label = label;
        this.image = image;
        this.osType = osType;
        this.command = command;
        this.rootFs = rootFs;
        this.ports = ports;
        this.cpu = cpu;
        this.memory = memory;
    }

    public void provisionAgents(AciCloud cloud, AciAgent agent) throws Exception {
        AciService.createDeployment(cloud, this, agent);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getImage() {
        return image;
    }

    public String getOsType() {
        return osType;
    }

    public String getLocation() {
        return location;
    }

    public String getCommand() {
        return command;
    }

    public String getRootFs() {
        return rootFs;
    }

    public List<AciPort> getPorts() {
        return ports;
    }

    public String getCpu() {
        return cpu;
    }

    public String getMemory() {
        return memory;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AciContainerTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }

        public ListBoxModel doFillLocationItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("westus");
            model.add("eastus");
            return model;
        }

        public ListBoxModel doFillOsTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Linux");
            model.add("Windows");
            return model;
        }
    }
}
