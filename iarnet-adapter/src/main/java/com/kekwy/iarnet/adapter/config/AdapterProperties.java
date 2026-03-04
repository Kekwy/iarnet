package com.kekwy.iarnet.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "iarnet.adapter")
public class AdapterProperties {

    private String name;
    private String description = "";
    private String type = "docker";
    private ControlPlane controlPlane = new ControlPlane();
    private List<String> tags = List.of();
    private ResourceConfig resource = new ResourceConfig();
    private String artifactDir = "/tmp/iarnet-adapter/artifacts";
    private DockerConfig docker = new DockerConfig();
    private K8sConfig k8s = new K8sConfig();

    // ===== Getters / Setters =====

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public ControlPlane getControlPlane() { return controlPlane; }
    public void setControlPlane(ControlPlane controlPlane) { this.controlPlane = controlPlane; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public ResourceConfig getResource() { return resource; }
    public void setResource(ResourceConfig resource) { this.resource = resource; }

    public String getArtifactDir() { return artifactDir; }
    public void setArtifactDir(String artifactDir) { this.artifactDir = artifactDir; }

    public DockerConfig getDocker() { return docker; }
    public void setDocker(DockerConfig docker) { this.docker = docker; }

    public K8sConfig getK8s() { return k8s; }
    public void setK8s(K8sConfig k8s) { this.k8s = k8s; }

    // ===== 嵌套配置类 =====

    public static class ControlPlane {
        private String host = "127.0.0.1";
        private int port = 9090;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    public static class ResourceConfig {
        private double cpu = 4.0;
        private String memory = "8Gi";
        private double gpu = 0;

        public double getCpu() { return cpu; }
        public void setCpu(double cpu) { this.cpu = cpu; }
        public String getMemory() { return memory; }
        public void setMemory(String memory) { this.memory = memory; }
        public double getGpu() { return gpu; }
        public void setGpu(double gpu) { this.gpu = gpu; }
    }

    public static class DockerConfig {
        private String host = "unix:///var/run/docker.sock";
        private String network = "";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getNetwork() { return network; }
        public void setNetwork(String network) { this.network = network; }
    }

    public static class K8sConfig {
        private String kubeconfig = "";
        private boolean inCluster = false;
        private String namespace = "default";

        public String getKubeconfig() { return kubeconfig; }
        public void setKubeconfig(String kubeconfig) { this.kubeconfig = kubeconfig; }
        public boolean isInCluster() { return inCluster; }
        public void setInCluster(boolean inCluster) { this.inCluster = inCluster; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
    }
}
