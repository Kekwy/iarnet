package com.kekwy.iarnet.adapter.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 管理 artifact 在设备上的本地存储。
 */
public class ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStore.class);

    private final Path baseDir;

    public ArtifactStore(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 artifact 存储目录: " + baseDir, e);
        }
    }

    /**
     * 将输入流写入到 artifactId 对应的文件中。
     *
     * @return 文件在设备上的绝对路径
     */
    public Path store(String artifactId, String fileName, InputStream data) throws IOException {
        Path artifactDir = baseDir.resolve(artifactId);
        Files.createDirectories(artifactDir);

        String targetName = (fileName != null && !fileName.isBlank()) ? fileName : artifactId;
        Path targetFile = artifactDir.resolve(targetName);

        try (OutputStream out = Files.newOutputStream(targetFile)) {
            data.transferTo(out);
        }

        log.info("Artifact 已存储: artifactId={}, path={}", artifactId, targetFile);
        return targetFile;
    }

    /**
     * 获取 artifact 所在目录。
     */
    public Path getArtifactDir(String artifactId) {
        return baseDir.resolve(artifactId);
    }

    /**
     * 检查 artifact 是否已存在。
     */
    public boolean exists(String artifactId) {
        return Files.isDirectory(baseDir.resolve(artifactId));
    }

    /** 存放各实例函数描述文件的子目录名 */
    private static final String FUNCTIONS_SUBDIR = "_functions";

    /**
     * 将函数描述（Proto 二进制）写入实例专属文件，供部署时挂载到 Actor 容器。
     *
     * @param instanceId 实例 ID
     * @param descriptorBytes FunctionDescriptor 的 proto 序列化字节
     * @return 写入后的文件路径（主机路径，用于 Docker bind mount）
     */
    public Path storeFunctionDescriptor(String instanceId, byte[] descriptorBytes) throws IOException {
        Path dir = baseDir.resolve(FUNCTIONS_SUBDIR).resolve(instanceId);
        Files.createDirectories(dir);
        Path file = dir.resolve("function.pb");
        Files.write(file, descriptorBytes != null ? descriptorBytes : new byte[0]);
        log.info("函数描述已写入: instanceId={}, path={}", instanceId, file);
        return file;
    }
}
