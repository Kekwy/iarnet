package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.proto.common.ResourceSpec;

public class ExecutionConfig {

    private static final ResourceSpec DEFAULT_RESOURCE = ResourceSpec.newBuilder()
            .setCpu(0.5).setMemory("256Mi").setGpu(0)
            .build();

    public interface ResourceSpecBuilder {
        ResourceSpecBuilder cpu(double cpu);

        ResourceSpecBuilder memory(String memory);

        ResourceSpecBuilder gpu(double gpu);

        ResourceSpec build();
    }

    @FunctionalInterface
    public interface ResourceConfigurer {
        ResourceSpecBuilder configure(ResourceSpecBuilder builder);
    }

    private static class ResourceSpecBuilderImpl implements ResourceSpecBuilder {
        private double cpu;
        private String memory;
        private double gpu;

        @Override
        public ResourceSpecBuilder cpu(double cpu) {
            this.cpu = cpu;
            return this;
        }

        @Override
        public ResourceSpecBuilder memory(String memory) {
            this.memory = memory;
            return this;
        }

        @Override
        public ResourceSpecBuilder gpu(double gpu) {
            this.gpu = gpu;
            return this;
        }

        @Override
        public ResourceSpec build() {
            return ResourceSpec.newBuilder()
                    .setCpu(cpu).setMemory(memory).setGpu(gpu)
                    .build();
        }
    }

    private int replicas;

    private ResourceSpec resource;

    private ExecutionConfig() {
        this.replicas = 1;
        this.resource = DEFAULT_RESOURCE;
    }

    public static ExecutionConfig of() {
        return new ExecutionConfig();
    }

    public int getReplicas() {
        return replicas;
    }

    public ResourceSpec getResourceSpec() {
        return resource;
    }

    public ExecutionConfig replicas(int replicas) {
        if (replicas <= 0) {
            throw new IllegalArgumentException("replicas must be greater than 0");
        }
        this.replicas = replicas;
        return this;
    }

    public ExecutionConfig resource(ResourceConfigurer configurer) {
        ResourceSpecBuilder builder = configurer.configure(new ResourceSpecBuilderImpl());
        this.resource = builder.build();
        return this;
    }

    @Override
    public String toString() {
        return "StepConfig{" +
                "replicas=" + replicas +
                ", resource=" + resource +
                '}';
    }
}
