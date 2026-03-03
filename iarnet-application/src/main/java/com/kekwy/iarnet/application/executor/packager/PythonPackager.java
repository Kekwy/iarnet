package com.kekwy.iarnet.application.executor.packager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Python 源码打包器：
 * <ol>
 *   <li>使用 conda 创建临时环境并安装 requirements.txt 中的依赖</li>
 *   <li>通过 {@code conda-pack} 将环境打包为 tar.gz</li>
 *   <li>将源码一同打入 tar.gz，使产物可直接分发执行</li>
 * </ol>
 *
 * <p>要求宿主机已安装 conda 和 conda-pack ({@code conda install conda-pack})。
 */
public class PythonPackager implements Packager {

    private static final Logger log = LoggerFactory.getLogger(PythonPackager.class);

    @Override
    public Path pack(Path sourcePath, Path outputDir) {
        Path requirementsFile = sourcePath.resolve("requirements.txt");
        if (!Files.exists(requirementsFile)) {
            throw new IllegalStateException(
                    "requirements.txt 不存在: " + sourcePath + "，Python 源码目录必须包含 requirements.txt");
        }

        String envName = "iarnet-py-" + System.currentTimeMillis();
        Path artifactFile = outputDir.resolve("python-env.tar.gz");
        Path logFile = outputDir.resolve("python-build.log");

        try {
            // 1. 创建 conda 环境
            log.info("创建 conda 环境: {}", envName);
            runCommand(new String[]{
                    "conda", "create", "-n", envName, "-y", "python=3.10"
            }, sourcePath, logFile);

            // 2. 安装依赖
            log.info("安装 Python 依赖: {}", requirementsFile);
            runCommand(new String[]{
                    "conda", "run", "-n", envName,
                    "pip", "install", "-r", requirementsFile.toAbsolutePath().toString()
            }, sourcePath, logFile);

            // 3. 将源码复制到 conda 环境的 site-packages 目录以便分发
            runCommand(new String[]{
                    "conda", "run", "-n", envName,
                    "pip", "install", "-e", sourcePath.toAbsolutePath().toString()
            }, sourcePath, logFile);

            // 4. 使用 conda-pack 打包
            log.info("使用 conda-pack 打包环境: {}", envName);
            runCommand(new String[]{
                    "conda-pack", "-n", envName,
                    "-o", artifactFile.toAbsolutePath().toString(),
                    "--force"
            }, sourcePath, logFile);

            log.info("Python artifact 已生成: {}", artifactFile);
            return artifactFile;
        } finally {
            // 5. 清理临时 conda 环境
            try {
                runCommand(new String[]{
                        "conda", "env", "remove", "-n", envName, "-y"
                }, sourcePath, logFile);
            } catch (Exception e) {
                log.warn("清理 conda 环境失败: {}", envName, e);
            }
        }
    }

    private void runCommand(String[] command, Path workDir, Path logFile) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "命令超时: " + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "命令失败, exitCode=" + process.exitValue()
                                + ", cmd=" + String.join(" ", command)
                                + ", 日志: " + logFile);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("执行命令异常: " + String.join(" ", command), e);
        }
    }
}
