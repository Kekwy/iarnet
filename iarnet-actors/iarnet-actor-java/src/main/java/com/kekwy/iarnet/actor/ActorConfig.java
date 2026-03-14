package com.kekwy.iarnet.actor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Actor 运行时配置，从环境变量解析。
 */
public final class ActorConfig {

    private static final String ENV_ACTOR_ID = "IARNET_ACTOR_ID";
    private static final String ENV_ACTOR_REGISTRY_ADDR = "IARNET_ACTOR_REGISTRY_ADDR";
    private static final String ENV_ACTOR_FUNCTION_FILE = "IARNET_ACTOR_FUNCTION_FILE";
    private static final String ENV_ARTIFACT_PATH = "IARNET_ARTIFACT_PATH";
    private static final String ENV_CONDITION_FUNCTIONS_DIR = "IARNET_CONDITION_FUNCTIONS_DIR";

    private static final String DEFAULT_REGISTRY_ADDR = "127.0.0.1:10000";

    private final String actorId;
    private final String registryAddr;
    private final Path functionFile;
    private final Path artifactPath;
    private final Path conditionFunctionsDir;

    private ActorConfig(String actorId, String registryAddr, Path functionFile,
                        Path artifactPath, Path conditionFunctionsDir) {
        this.actorId = actorId;
        this.registryAddr = registryAddr;
        this.functionFile = functionFile;
        this.artifactPath = artifactPath;
        this.conditionFunctionsDir = conditionFunctionsDir;
    }

    /**
     * 从环境变量解析配置，校验必填项。
     *
     * @return 解析后的配置
     * @throws IllegalArgumentException 若必填项未设置或无效
     */
    public static ActorConfig fromEnvironment() {
        String actorId = System.getenv(ENV_ACTOR_ID);
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("环境变量 " + ENV_ACTOR_ID + " 未设置");
        }

        String registryAddr = System.getenv(ENV_ACTOR_REGISTRY_ADDR);
        if (registryAddr == null || registryAddr.isBlank()) {
            registryAddr = DEFAULT_REGISTRY_ADDR;
        }

        String functionFileStr = System.getenv(ENV_ACTOR_FUNCTION_FILE);
        if (functionFileStr == null || functionFileStr.isBlank()) {
            throw new IllegalArgumentException("环境变量 " + ENV_ACTOR_FUNCTION_FILE + " 未设置");
        }
        Path functionFile = Path.of(functionFileStr);

        Path artifactPath = null;
        String artifactPathStr = System.getenv(ENV_ARTIFACT_PATH);
        if (artifactPathStr != null && !artifactPathStr.isBlank()) {
            artifactPath = Path.of(artifactPathStr);
        }

        Path conditionFunctionsDir = null;
        String conditionDirStr = System.getenv(ENV_CONDITION_FUNCTIONS_DIR);
        if (conditionDirStr != null && !conditionDirStr.isBlank()) {
            conditionFunctionsDir = Path.of(conditionDirStr);
        }

        return new ActorConfig(actorId, registryAddr, functionFile, artifactPath, conditionFunctionsDir);
    }

    public String getActorId() {
        return actorId;
    }

    public String getRegistryAddr() {
        return registryAddr;
    }

    public Path getFunctionFile() {
        return functionFile;
    }

    /** 可选，用户 JAR 路径。 */
    public Path getArtifactPath() {
        return artifactPath;
    }

    /** 可选，条件函数目录。 */
    public Path getConditionFunctionsDir() {
        return conditionFunctionsDir;
    }

    public boolean hasArtifact() {
        return artifactPath != null;
    }

    public boolean hasConditionFunctions() {
        return conditionFunctionsDir != null;
    }
}
