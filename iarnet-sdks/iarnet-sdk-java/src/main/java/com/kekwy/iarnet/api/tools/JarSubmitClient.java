package com.kekwy.iarnet.api.tools;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.api.util.IDUtil;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单命令行客户端：向已运行的 control-plane 提交打包好的 JAR（artifact）。
 * <p>
 * 它通过 gRPC 调用 WorkflowService.SubmitWorkflow，将 JAR 字节放在
 * {@link SubmitWorkflowRequest#artifact} 字段中，服务端会将其落盘到对应
 * Application 的 Workspace artifacts/ 目录中，便于后续调度测试。
 *
 * <p>示例用法：
 * <pre>
 *   java -cp iarnet-api-java.jar com.kekwy.iarnet.api.tools.JarSubmitClient \
 *     --host=127.0.0.1 \
 *     --port=50051 \
//  *     --workflow-id=test-workflow-001 \
 *     --jar=/path/to/app.jar
 * </pre>
 *
 * 其中：
 * <ul>
 *   <li>--host        控制平面 gRPC Host，默认 localhost</li>
 *   <li>--port        控制平面 gRPC 端口，必填</li>
 *   <li>--jar         本地 JAR 路径，必填</li>
 * </ul>
 */
public class JarSubmitClient {

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

        String artifactName = jarPath.getFileName().toString();

        // workflow-id 由客户端自动生成，用于在服务端区分本次上传记录。
        String workflowId = IDUtil.genUUID();

        // 构造一个仅携带 workflow_id 的最小 WorkflowGraph，
        // 应用 ID 由控制平面在接收请求时自动生成并创建 Workspace。
        WorkflowGraph graph = WorkflowGraph.newBuilder()
                .setWorkflowId(workflowId)
                .build();

        SubmitWorkflowRequest request = SubmitWorkflowRequest.newBuilder()
                .setGraph(graph)
                .setArtifact(ByteString.copyFrom(bytes))
                .setArtifactName(artifactName)
                .build();

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            WorkflowServiceGrpc.WorkflowServiceBlockingStub stub =
                    WorkflowServiceGrpc.newBlockingStub(channel);

            SubmitWorkflowResponse resp = stub.submitWorkflow(request);
            if (resp.getStatus() == SubmissionStatus.ACCEPTED) {
                System.out.println("JAR 提交成功");
                System.out.println("submissionId: " + resp.getSubmissionId());
                System.out.println("message:      " + resp.getMessage());
                System.exit(0);
            } else {
                System.err.println("JAR 提交被拒绝: " + resp.getMessage());
                System.exit(1);
            }
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
        System.out.println("  java -cp iarnet-api-java.jar com.kekwy.iarnet.api.tools.JarSubmitClient \\");
        System.out.println("    --host=127.0.0.1 \\");
        System.out.println("    --port=50051 \\");
        System.out.println("    --jar=/path/to/app.jar");
        System.out.println();
    }
}

