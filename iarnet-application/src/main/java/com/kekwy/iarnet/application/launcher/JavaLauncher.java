package com.kekwy.iarnet.application.launcher;

import com.kekwy.iarnet.model.ID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Java 应用启动器。
 * <p>
 * 约定：用户提交的 Java 应用结构与 {@code iarnet-example/java} 相同，
 * 即 Maven 项目，根目录或 {@code java/} 子目录下包含 {@code pom.xml}。
 * 本类会在临时目录中拷贝该项目并执行 {@code mvn clean package} 构建 fat jar，
 * 并返回生成的 jar 绝对路径。
 *
 * <p>注意：是否为 fat jar 取决于用户 pom 中配置的插件（如 maven-shade-plugin / spring-boot-maven-plugin）。
 */
@Slf4j
public class JavaLauncher implements Launcher {

    /** 构建产物路径，供后续 launch 使用 */
    private String artifactPath;

    @Override
    public String build(ID applicationID, String workspaceDir) {
        try {
            Path workspacePath = Paths.get(workspaceDir);
            Path sourceDir = workspacePath.resolve(workspaceDir).resolve("source");
            if (!Files.isDirectory(sourceDir)) {
                throw new IllegalArgumentException("workspaceDir 不是有效目录: " + workspaceDir);
            }

            // 在系统临时目录下创建构建目录，避免直接修改工作空间
            Path buildRoot = Files.createTempDirectory(
                    "iarnet-java-build-" + (applicationID != null ? applicationID.getValue() : "unknown") + "-");
            log.info("为应用 {} 创建临时构建目录: {}", applicationID, buildRoot.toAbsolutePath());

            // 拷贝工作空间到临时构建目录
            copyDirectoryRecursively(sourceDir, buildRoot);

            // 支持两种结构：
            // 1) <buildRoot>/pom.xml
            // 2) <buildRoot>/java/pom.xml （类似 iarnet-example/java）
            Path pom = buildRoot.resolve("pom.xml");
            if (!Files.exists(pom)) {
                Path javaPom = buildRoot.resolve("java").resolve("pom.xml");
                if (Files.exists(javaPom)) {
                    pom = javaPom;
                } else {
                    throw new IllegalStateException("在工作空间中未找到 pom.xml，无法构建 Java 应用");
                }
            }

            Path projectDir = pom.getParent();

            // 准备构建日志文件：保存在原始工作空间目录下，便于前端通过后端接口读取
            Path logFile = workspacePath.resolve("build.log");

            // 调用 Maven 构建（要求运行环境已安装 mvn 命令）
            // 移除 -q 参数，使用 -X 显示详细日志，便于排查构建问题
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn",
                    "-X",
                    "-DskipTests=true",
                    "clean",
                    "package"
            );
            pb.directory(projectDir.toFile());
            pb.redirectErrorStream(true);

            log.info("开始构建 Java 应用，项目目录: {}", projectDir.toAbsolutePath());
            Process process = pb.start();


            try {
                // 最长等待 30 分钟，Maven 构建可能需要较长时间
                boolean finished = process.waitFor(30, TimeUnit.MINUTES);

                if (!finished) {
                    process.destroyForcibly();
                    try {
                        FileSystemUtils.deleteRecursively(buildRoot);
                    } catch (IOException ignored) {
                    }
                    throw new IllegalStateException("Maven 构建超时，已强制终止");
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.error("Maven 构建失败，exitCode={}，输出:\n{}", exitCode);
                    try {
                        FileSystemUtils.deleteRecursively(buildRoot);
                    } catch (IOException ignored) {
                    }
                    throw new IllegalStateException("Maven 构建失败，exitCode=" + exitCode );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                try {
                    FileSystemUtils.deleteRecursively(buildRoot);
                } catch (IOException ignored) {
                }
                throw new RuntimeException("Maven 构建过程中被中断", e);
            }

            // 从 target 目录中选择一个 jar（通常是 fat jar）。
            Path targetDir = projectDir.resolve("target");
            if (!Files.isDirectory(targetDir)) {
                throw new IllegalStateException("未找到目标目录: " + targetDir);
            }

            try (Stream<Path> files = Files.list(targetDir)) {
                Path jar = files
                        .filter(p -> p.toString().endsWith(".jar"))
                        // 优先选择体积最大的 jar，通常是 fat jar
                        .max(Comparator.comparingLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        }))
                        .orElseThrow(() -> new IllegalStateException("target 目录中未找到任何 jar 文件: " + targetDir));

                artifactPath = jar.toAbsolutePath().toString();
                log.info("Java 应用构建完成，产物: {}", artifactPath);
                return artifactPath;
            }
        } catch (IOException e) {
            throw new RuntimeException("构建 Java 应用失败（IO 异常）", e);
        }
    }

    @Override
    public boolean launch() {
        // 后续可在此处实现通过 `java -jar artifactPath` 启动应用的逻辑
        // 目前仅完成构建流程，启动由后续 Runner 结合容器/进程管理实现。
        log.warn("JavaLauncher.launch 尚未实现，artifactPath={}", artifactPath);
        return false;
    }

    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path dest = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        if (!Files.exists(dest)) {
                            Files.createDirectories(dest);
                        }
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("复制目录失败: " + path, e);
                }
            });
        }
    }

    private void readStream(InputStream is, String prefix, StringBuilder collector, Path logFile) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             BufferedWriter writer = Files.newBufferedWriter(
                     logFile,
                     StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            String line;
            while ((line = br.readLine()) != null) {
                log.info("{} {}", prefix, line);
                collector.append(line).append('\n');
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.warn("{} 读取输出失败: {}", prefix, e.getMessage());
        }
    }
}
