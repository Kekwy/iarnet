package com.kekwy.iarnet.application.launcher;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.common.model.ID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
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
 * <p>
 * 注意：是否为 fat jar 取决于用户 pom 中配置的插件（如 maven-shade-plugin /
 * spring-boot-maven-plugin）。
 */
@Slf4j
public class JavaLauncher implements Launcher {

    private final int grpcPort;

    public JavaLauncher(int grpcPort) {
        this.grpcPort = grpcPort;
    }

    @Override
    public boolean launch(Workspace workspace) {
        Path workspaceDir = workspace.getWorkspaceDir();
        try {
            Path sourceDir = workspace.getSourceDir();
            if (!Files.isDirectory(sourceDir)) {
                throw new IllegalArgumentException("workspaceDir 不是有效目录: " + workspaceDir);
            }

            ID applicationID = workspace.getApplicationID();
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
            Path logFile = workspace.getBuildLogFile();

            // 调用 Maven 构建（要求运行环境已安装 mvn 命令）
            // 使用 -X 显示详细日志，便于排查构建问题；所有输出重定向到日志文件
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn",
                    "-X",
                    "-DskipTests=true",
                    "clean",
                    "package");
            pb.directory(projectDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            log.info("开始构建 Java 应用，项目目录: {}，日志文件: {}",
                    projectDir.toAbsolutePath(), logFile.toAbsolutePath());
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
                    log.error("Maven 构建失败，exitCode={}，详情见日志文件: {}", exitCode, logFile.toAbsolutePath());
                    try {
                        FileSystemUtils.deleteRecursively(buildRoot);
                    } catch (IOException ignored) {
                    }
                    throw new IllegalStateException("Maven 构建失败，exitCode=" + exitCode
                            + "，详情见日志文件: " + logFile.toAbsolutePath());
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

                // 将构建产物复制到 workspace 的 artifacts 目录
                Path artifactJar = workspace.addArtifact(jar);

                log.info("Java 应用构建完成，产物已复制到 workspace: {}", artifactJar.toAbsolutePath());

                // 启动应用进程，并将运行日志写入 workspace 的 app 日志文件
                Path appLogFile = workspace.getAppLogFile();
                Files.createDirectories(appLogFile.getParent());

                ProcessBuilder runPb = new ProcessBuilder(
                        "java",
                        "-jar",
                        artifactJar.toAbsolutePath().toString()
                );

                // 通过环境变量将当前应用 ID 和 gRPC 端口传递给运行中的进程
                if (applicationID != null && applicationID.getValue() != null) {
                    runPb.environment().put("IARNET_APP_ID", applicationID.getValue());
                }
                runPb.environment().put("IARNET_GRPC_PORT", String.valueOf(grpcPort));
                runPb.directory(workspaceDir.toFile());
                runPb.redirectErrorStream(true);
                runPb.redirectOutput(ProcessBuilder.Redirect.appendTo(appLogFile.toFile()));

                Process appProcess = runPb.start();
                log.info("已启动 Java 应用进程: pid={}, jar={}, logFile={}",
                        appProcess.pid(), artifactJar.toAbsolutePath(), appLogFile.toAbsolutePath());

                // 清理临时构建目录
                try {
                    FileSystemUtils.deleteRecursively(buildRoot);
                } catch (IOException e) {
                    log.warn("清理临时构建目录失败: {}", buildRoot.toAbsolutePath(), e);
                }

                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException("构建 Java 应用失败（IO 异常）", e);
        }
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
}
