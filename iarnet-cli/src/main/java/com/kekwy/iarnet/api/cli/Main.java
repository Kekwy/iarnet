package com.kekwy.iarnet.api.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.proto.application.ApplicationServiceGrpc;
import com.kekwy.iarnet.proto.application.InputEntry;
import com.kekwy.iarnet.proto.application.InputGroup;
import com.kekwy.iarnet.proto.application.SubmitJarRequest;
import com.kekwy.iarnet.proto.application.SubmitJarResponse;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputRequest;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputResponse;
import com.kekwy.iarnet.proto.common.Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Main {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);

        String host = options.getOrDefault("host", "localhost");
        String portStr = options.get("port");
        String jarPathStr = options.get("jar");
        String inputPathStr = options.get("input");
        String inputsPathStr = options.get("inputs");

        if (portStr == null || jarPathStr == null) {
            printUsage();
            System.exit(1);
        }
        if (inputPathStr != null && !inputPathStr.isBlank() && inputsPathStr != null && !inputsPathStr.isBlank()) {
            System.err.println("--input 与 --inputs 不能同时指定，请二选一");
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

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .maxInboundMessageSize(64 * 1024 * 1024)
                .usePlaintext()
                .build();

        try {
            ApplicationServiceGrpc.ApplicationServiceBlockingStub stub =
                    ApplicationServiceGrpc.newBlockingStub(channel);

            if (inputsPathStr != null && !inputsPathStr.isBlank()) {
                Path inputsPath = Paths.get(inputsPathStr).toAbsolutePath().normalize();
                if (!Files.isRegularFile(inputsPath)) {
                    System.err.println("多组输入 JSON 文件不存在或不可读: " + inputsPath);
                    System.exit(1);
                }
                List<InputGroup> inputGroups = parseInputGroupsJson(inputsPath);
                SubmitJarWithInputRequest request = SubmitJarWithInputRequest.newBuilder()
                        .setContent(ByteString.copyFrom(bytes))
                        .addAllInputGroups(inputGroups)
                        .build();
                SubmitJarWithInputResponse resp = stub.submitJarWithInput(request);
                System.out.println("JAR 与 " + inputGroups.size() + " 组输入提交完成，服务端返回: " + resp.getMsg());
            } else if (inputPathStr != null && !inputPathStr.isBlank()) {
                Path inputPath = Paths.get(inputPathStr).toAbsolutePath().normalize();
                if (!Files.isRegularFile(inputPath)) {
                    System.err.println("输入 JSON 文件不存在或不可读: " + inputPath);
                    System.exit(1);
                }
                List<InputEntry> inputEntries = parseInputJson(inputPath);
                SubmitJarWithInputRequest request = SubmitJarWithInputRequest.newBuilder()
                        .setContent(ByteString.copyFrom(bytes))
                        .addAllInputs(inputEntries)
                        .build();
                SubmitJarWithInputResponse resp = stub.submitJarWithInput(request);
                System.out.println("JAR 与输入提交完成，服务端返回: " + resp.getMsg());
            } else {
                SubmitJarRequest request = SubmitJarRequest.newBuilder()
                        .setContent(ByteString.copyFrom(bytes))
                        .build();
                SubmitJarResponse resp = stub.submitJar(request);
                System.out.println("JAR 提交完成，服务端返回信息: " + resp.getMsg());
            }
            System.exit(0);
        } catch (StatusRuntimeException e) {
            System.err.println("gRPC 调用失败: " + e.getStatus());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("读取或解析输入 JSON 失败: " + e.getMessage());
            System.exit(1);
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * 从 JSON 文件解析输入。JSON 为对象，key 为参数名，value 为 proto Value 的 JSON 表示（如 {"stringValue":"x"} 或 {"int32Value":42}）。
     */
    private static List<InputEntry> parseInputJson(Path inputPath) throws IOException {
        String content = Files.readString(inputPath);
        JsonNode root = JSON.readTree(content);
        if (!root.isObject()) {
            throw new IOException("输入 JSON 必须为对象，key 为参数名，value 为 Value 的 JSON");
        }
        List<InputEntry> entries = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String paramName = e.getKey();
            JsonNode valueNode = e.getValue();
            Value.Builder valueBuilder = Value.newBuilder();
            JsonFormat.parser().merge(valueNode.toString(), valueBuilder);
            entries.add(InputEntry.newBuilder()
                    .setKey(paramName)
                    .setValue(valueBuilder.build())
                    .build());
        }
        return entries;
    }

    /**
     * 从 JSON 文件解析多组输入。JSON 为数组，每个元素为对象（key 为参数名，value 为 proto Value 的 JSON）。
     */
    private static List<InputGroup> parseInputGroupsJson(Path inputPath) throws IOException {
        String content = Files.readString(inputPath);
        JsonNode root = JSON.readTree(content);
        if (!root.isArray()) {
            throw new IOException("多组输入 JSON 必须为数组，每个元素为对象");
        }
        List<InputGroup> groups = new ArrayList<>();
        for (JsonNode elem : root) {
            if (!elem.isObject()) {
                throw new IOException("多组输入数组中每个元素必须为对象");
            }
            List<InputEntry> entries = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = elem.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String paramName = e.getKey();
                JsonNode valueNode = e.getValue();
                Value.Builder valueBuilder = Value.newBuilder();
                JsonFormat.parser().merge(valueNode.toString(), valueBuilder);
                entries.add(InputEntry.newBuilder()
                        .setKey(paramName)
                        .setValue(valueBuilder.build())
                        .build());
            }
            groups.add(InputGroup.newBuilder().addAllEntries(entries).build());
        }
        return groups;
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
        System.out.println("用法: 通过 gRPC 向 control-plane 提交 JAR（artifact），可选携带单组或多组输入");
        System.out.println();
        System.out.println("  java -cp ... com.kekwy.iarnet.api.cli.Main \\");
        System.out.println("    --host=127.0.0.1 \\");
        System.out.println("    --port=50051 \\");
        System.out.println("    --jar=/path/to/app.jar \\");
        System.out.println("    [--input=/path/to/input.json]  单组输入（对象）");
        System.out.println("    [--inputs=/path/to/inputs.json] 多组输入（数组），与 --input 二选一");
        System.out.println();
        System.out.println("  --input 格式: 对象，key 为参数名，value 为 proto Value 的 JSON");
        System.out.println("    例: {\"frame\": {\"structValue\": {\"fields\": [...]}}}");
        System.out.println("  --inputs 格式: 数组，每个元素为上述对象，每组触发一次 execute");
        System.out.println("    例: [ {\"frame\": {...}}, {\"frame\": {...}} ]");
        System.out.println();
    }
}
