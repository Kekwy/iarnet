package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.proto.common.ResourceSpec;
import com.kekwy.iarnet.sdk.exception.IarnetValidationException;
import com.kekwy.iarnet.sdk.function.TaskFunction;

/**
 * 节点执行配置。
 * <p>
 * 用于 {@link Flow#then(String, TaskFunction, ExecutionConfig)} 等方法，
 * 指定节点的副本数（replicas）和资源规格（CPU、内存、GPU）。
 * 默认：replicas=1，CPU=0.5，memory=256Mi，gpu=0。
 */
public class ExecutionConfig {

    private static final ResourceSpec DEFAULT_RESOURCE = ResourceSpec.newBuilder()
            .setCpu(0.5).setMemory("256Mi").setGpu(0)
            .build();

    /**
     * 资源规格构建器，用于流式配置 CPU、内存、GPU。
     */
    public interface ResourceSpecBuilder {
        ResourceSpecBuilder cpu(double cpu);

        ResourceSpecBuilder memory(String memory);

        ResourceSpecBuilder gpu(double gpu);

        ResourceSpec build();
    }

    /**
     * 资源配置函数，配合 {@link #resource(ResourceConfigurer)} 使用。
     */
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

    /**
     * 创建默认配置（replicas=1，CPU=0.5，memory=256Mi，gpu=0）。
     *
     * @return 默认 ExecutionConfig
     */
    public static ExecutionConfig of() {
        return new ExecutionConfig();
    }

    /** 节点副本数。 */
    public int getReplicas() {
        return replicas;
    }

    /** 资源规格。 */
    public ResourceSpec getResourceSpec() {
        return resource;
    }

    /**
     * 设置副本数。
     *
     * @param replicas 必须大于 0
     * @return 本对象，支持链式调用
     * @throws IarnetValidationException 若 replicas <= 0
     */
    public ExecutionConfig replicas(int replicas) {
        if (replicas <= 0) {
            throw new IarnetValidationException("replicas must be greater than 0");
        }
        this.replicas = replicas;
        return this;
    }

    /**
     * 通过 configurer 设置资源规格。
     *
     * @param configurer 配置函数，如 {@code cfg -> cfg.cpu(1.0).memory("512Mi")}
     * @return 本对象，支持链式调用
     */
    public ExecutionConfig resource(ResourceConfigurer configurer) {
        ResourceSpecBuilder builder = configurer.configure(new ResourceSpecBuilderImpl());
        this.resource = builder.build();
        return this;
    }

    @Override
    public String toString() {
        return "ExecutionConfig{" +
                "replicas=" + replicas +
                ", resource=" + resource +
                '}';
    }
}
