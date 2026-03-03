package com.kekwy.iarnet.application.executor.packager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Java 源码打包器：在指定目录执行 {@code mvn clean package}，
 * 选取 target/ 中最大的 JAR 作为 artifact。
 */
public class JavaPackager implements Packager {

    private static final Logger log = LoggerFactory.getLogger(JavaPackager.class);

    @Override
    public Path pack(Path sourcePath, Path outputDir) {
        Path pomFile = sourcePath.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            Path javaPom = sourcePath.resolve("java").resolve("pom.xml");
            if (Files.exists(javaPom)) {
                sourcePath = javaPom.getParent();
            } else {
                throw new IllegalStateException("未找到 pom.xml: " + sourcePath);
            }
        }

        log.info("开始 Maven 打包: {}", sourcePath);

        Path logFile = outputDir.resolve("java-build.log");
        ProcessBuilder pb = new ProcessBuilder(
                "mvn", "-DskipTests=true", "clean", "package");
        pb.directory(sourcePath.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Maven 打包超时");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Maven 打包失败, exitCode=" + process.exitValue() + ", 日志: " + logFile);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Maven 打包异常", e);
        }

        Path targetDir = sourcePath.resolve("target");
        try (Stream<Path> files = Files.list(targetDir)) {
            Path jar = files
                    .filter(p -> p.toString().endsWith(".jar"))
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0L; }
                    }))
                    .orElseThrow(() -> new IllegalStateException("target 中未找到 JAR: " + targetDir));

            Path dest = outputDir.resolve(jar.getFileName());
            Files.copy(jar, dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Java artifact 已生成: {}", dest);
            return dest;
        } catch (IOException e) {
            throw new RuntimeException("读取 Maven 构建产物失败", e);
        }
    }
}
