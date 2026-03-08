package com.kekwy.iarnet.api.cli;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.api.ApplicationServiceGrpc;
import com.kekwy.iarnet.proto.api.SubmitJarRequest;
import com.kekwy.iarnet.proto.api.SubmitJarResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);

        String host = options.getOrDefault("host", "localhost");
        String portStr = options.get("port");
        String jarPathStr = options.get("jar");

        if (portStr == null || jarPathStr == null) {
            printUsage();
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("无效的端口号: " + portStr);
            System.exit(1);
            return;
        }

        Path jarPath = Paths.get(jarPathStr).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jarPath)) {
            System.err.println("JAR 文件不存在或不可读: " + jarPath);
            System.exit(1);
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(jarPath);
        } catch (IOException e) {
            System.err.println("读取 JAR 文件失败: " + e.getMessage());
            System.exit(1);
            return;
        }

        SubmitJarRequest request = SubmitJarRequest.newBuilder()
                .setContent(ByteString.copyFrom(bytes))
                .build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                // 调整客户端可接收的最大入站消息大小（与服务端保持一致，64MB）
                .maxInboundMessageSize(64 * 1024 * 1024)
                .usePlaintext()
                .build();

        try {
            ApplicationServiceGrpc.ApplicationServiceBlockingStub stub =
                    ApplicationServiceGrpc.newBlockingStub(channel);

            SubmitJarResponse resp = stub.submitJar(request);
            System.out.println("JAR 提交完成，服务端返回信息: " + resp.getMsg());
            System.exit(0);
        } catch (StatusRuntimeException e) {
            System.err.println("gRPC 调用失败: " + e.getStatus());
            System.exit(1);
        } finally {
            channel.shutdownNow();
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) {
            return map;
        }
        for (String arg : args) {
            if (arg == null) continue;
            if (!arg.startsWith("--")) continue;
            String withoutPrefix = arg.substring(2);
            int idx = withoutPrefix.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = withoutPrefix.substring(0, idx);
            String value = withoutPrefix.substring(idx + 1);
            map.put(key, value);
        }
        return map;
    }

    private static void printUsage() {
        System.out.println("用法: 通过 gRPC 向 control-plane 提交 JAR（artifact）");
        System.out.println();
        System.out.println("  java -cp iarnet-api-java.jar com.kekwy.iarnet.api.cli.Main \\");
        System.out.println("    --host=127.0.0.1 \\");
        System.out.println("    --port=50051 \\");
        System.out.println("    --jar=/path/to/app.jar");
        System.out.println();
    }
}
