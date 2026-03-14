package com.kekwy.iarnet.sdk;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.workflow.SubmissionStatus;
import com.kekwy.iarnet.proto.workflow.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.workflow.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.workflow.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.common.TypeKind;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.proto.workflow.NodeConfig;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.proto.workflow.WorkflowInput;
import com.kekwy.iarnet.sdk.exception.IarnetCommunicationException;
import com.kekwy.iarnet.sdk.exception.IarnetConfigurationException;
import com.kekwy.iarnet.sdk.exception.IarnetSubmissionException;
import com.kekwy.iarnet.sdk.exception.IarnetValidationException;
import com.kekwy.iarnet.sdk.function.ConditionFunction;
import com.kekwy.iarnet.sdk.function.Function;
import com.kekwy.iarnet.sdk.function.GoTaskFunction;
import com.kekwy.iarnet.sdk.function.OutputFunction;
import com.kekwy.iarnet.sdk.function.PythonTaskFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.function.CombineFunction;
import com.kekwy.iarnet.sdk.type.TypeToken;
import com.kekwy.iarnet.sdk.util.IDUtil;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeExtractor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工作流 DSL 入口。
 * <p>
 * 通过 {@link #create(String)} 创建实例，{@link #input(String)} 或 {@link #input(String, TypeToken)} 注册工作流输入参数
 *（名称与类型写入 WorkflowGraph，实际值在提交时由用户提供），返回的 {@link Flow} 可链式追加任务、条件分支、合并与输出。
 * {@link #buildGraph()} 构建图结构，{@link #execute()} 或 {@link #submit(WorkflowGraph, String, int)} 提交至 control-plane。
 * <p>
 * 典型用法：
 * <pre>{@code
 * Workflow w = Workflow.create("example");
 * w.input("src", new TypeToken<Integer>() {})
 *  .then("double", (Integer x) -> x * 2)
 *  .then("sink", Outputs.println());
 * w.execute();  // 需设置 IARNET_APP_ID、IARNET_GRPC_PORT
 * }</pre>
 */
public class Workflow {

    private static final Logger LOG = Logger.getLogger(Workflow.class.getName());

    private static final String ENV_APP_ID = "IARNET_APP_ID";
    private static final String ENV_GRPC_PORT = "IARNET_GRPC_PORT";
    private static final String ENV_EXTERNAL_SOURCE_BASE_DIR = "IARNET_EXTERNAL_SOURCE_BASE_DIR";
    private static final String DEFAULT_GRPC_HOST = "localhost";

    /** 约定：外部 Python 函数源码目录（相对于项目根） */
    private static final String CONVENTION_PYTHON_DIR = "resource/function/python";
    /** 约定：外部 Go 函数源码目录（相对于项目根） */
    private static final String CONVENTION_GO_DIR = "resource/function/go";

    /** 已导出的语言，避免重复复制。 */
    private static final Set<Lang> exportedLangs = new HashSet<>();

    private final String name;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    /**
     * 节点 ID -> 用户通过 returns() 传入的输出类型提示，用于类型擦除时兜底。
     */
    private final Map<String, java.lang.reflect.Type> nodeOutputTypeHints = new HashMap<>();

    /** 工作流输入参数定义（名称 + 类型），写入 WorkflowGraph.inputs。 */
    private final List<WorkflowInput> workflowInputs = new ArrayList<>();

    /** 入口节点 ID -> 关联的工作流输入参数名，写入 Node.input_param。 */
    private final Map<String, String> nodeInputParamMap = new HashMap<>();

    /** 每个源节点下一条条件边的 output_port 从 1 自增（与 Runtime 原逻辑一致，现上移到 SDK）。 */
    private final Map<String, Integer> nextConditionalPort = new HashMap<>();

    private Workflow(String name) {
        this.name = name;
    }

    // ======================== 工厂与入口 ========================

    /**
     * 创建指定名称的工作流。
     *
     * @param name 工作流名称
     * @return 新 Workflow 实例
     */
    public static Workflow create(String name) {
        return new Workflow(name);
    }

    /** 工作流名称。 */
    public String getName() {
        return name;
    }

    /**
     * 注册工作流输入参数，类型由下游 {@code then()} 推断。
     *
     * @param name 参数名
     * @param <T>  参数类型（由链式调用的下游函数推断）
     * @return Flow，可继续链式 then / combine / when
     */
    public <T> Flow<T> input(String name) {
        return new InputFlow<>(name, null);
    }

    /**
     * 注册工作流输入参数，并指定类型。
     *
     * @param name      参数名
     * @param typeToken 参数类型
     * @param <T>       参数类型
     * @return Flow，可继续链式 then / combine / when
     */
    public <T> Flow<T> input(String name, TypeToken<T> typeToken) {
        java.lang.reflect.Type t = typeToken.getType();
        if (t != null && t != Object.class) {
            workflowInputs.add(WorkflowInput.newBuilder()
                    .setName(name)
                    .setType(com.kekwy.iarnet.proto.Types.fromType(t))
                    .build());
        }
        return new InputFlow<>(name, t);
    }

    // ======================== 构建 WorkflowGraph ========================

    /**
     * 从环境变量 {@value #ENV_APP_ID} 读取 application ID 并构建图。
     *
     * @return WorkflowGraph
     * @throws IarnetConfigurationException 若环境变量未设置
     * @throws IarnetValidationException     若存在节点输出类型无法推断
     */
    public WorkflowGraph buildGraph() {
        String applicationId = System.getenv(ENV_APP_ID);
        if (applicationId == null || applicationId.isBlank()) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_APP_ID + " 未设置，无法确定 application ID");
        }
        return buildGraph(applicationId);
    }

    /**
     * 使用指定 application ID 构建图。
     *
     * @param applicationId 应用 ID
     * @return WorkflowGraph
     * @throws IarnetValidationException 若存在节点输出类型无法推断
     * @throws IarnetConfigurationException 若使用 Python/Go 但 ENV_EXTERNAL_SOURCE_DIR 未设置
     */
    public WorkflowGraph buildGraph(String applicationId) {
        List<Node> finalizedNodes = finalizeNodesWithTypes();
        List<Node> nodesWithInputParam = new ArrayList<>();
        for (Node node : finalizedNodes) {
            String inputParam = nodeInputParamMap.get(node.getId());
            if (inputParam != null) {
                nodesWithInputParam.add(node.toBuilder().setInputParam(inputParam).build());
            } else {
                nodesWithInputParam.add(node);
            }
        }
        Set<Lang> externalLangs = collectExternalLangs(nodesWithInputParam);
        if (!externalLangs.isEmpty()) {
            ensureExternalSourcesExported(externalLangs);
        }
        WorkflowGraph graph = WorkflowGraph.newBuilder()
                .setWorkflowId(IDUtil.genUUIDWith("wf"))
                .setApplicationId(applicationId)
                .setName(name)
                .addAllNodes(nodesWithInputParam)
                .addAllEdges(edges)
                .addAllInputs(workflowInputs)
                .build();
        LOG.log(Level.FINE, "Workflow graph built: name={0}, nodes={1}, edges={2}, inputs={3}",
                new Object[]{name, nodesWithInputParam.size(), edges.size(), workflowInputs.size()});
        return graph;
    }

    /**
     * 补齐所有有出边节点的 output_type。
     * <p>
     * 若反射推断失败，则从 returns() 的提示中获取；若仍无法获取则抛出
     * {@link IarnetValidationException}。Sink 节点（无出边）无需 output_type，跳过。
     */
    private List<Node> finalizeNodesWithTypes() {
        Set<String> hasOutgoing = new HashSet<>();
        for (Edge e : edges) {
            hasOutgoing.add(e.getFromNodeId());
        }
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            FunctionDescriptor fd = node.getFunction();
            if (!fd.hasOutputType() || fd.getOutputType().getKind() == TypeKind.TYPE_KIND_UNSPECIFIED) {
                if (!hasOutgoing.contains(node.getId())) {
                    result.add(node);
                    continue;
                }
                java.lang.reflect.Type hint = nodeOutputTypeHints.get(node.getId());
                if (hint == null) {
                    throw new IarnetValidationException(
                            "无法推断节点 " + node.getId() + " 的输出类型，请对该 flow 调用 .returns(new TypeToken<YourType>() {}) 提供类型提示");
                }
                FunctionDescriptor patched = fd.toBuilder()
                        .setOutputType(com.kekwy.iarnet.proto.Types.fromType(hint))
                        .build();
                result.add(node.toBuilder().setFunction(patched).build());
            } else {
                result.add(node);
            }
        }
        return result;
    }

    // ======================== gRPC 提交 ========================

    /**
     * 构建图并从环境变量 {@value #ENV_GRPC_PORT} 读取端口，提交至 localhost。
     *
     * @throws IarnetConfigurationException 若环境变量未设置或格式无效
     * @throws IarnetSubmissionException    若服务端拒绝提交
     * @throws IarnetCommunicationException 若 gRPC 调用失败
     */
    public void execute() {
        WorkflowGraph graph = buildGraph();

        String portStr = System.getenv(ENV_GRPC_PORT);
        if (portStr == null || portStr.isBlank()) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_GRPC_PORT + " 未设置，无法连接 control-plane gRPC 服务");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_GRPC_PORT + " 格式无效，期望整数: " + portStr, e);
        }
        LOG.log(Level.FINE, "Executing workflow: {0}, host={1}, port={2}", new Object[]{name, DEFAULT_GRPC_HOST, port});
        submit(graph, DEFAULT_GRPC_HOST, port);
    }

    /**
     * 将工作流图提交至 control-plane gRPC 服务。
     *
     * @param graph 工作流图
     * @param host  control-plane 地址
     * @param port  gRPC 端口
     * @throws IarnetSubmissionException    若服务端拒绝
     * @throws IarnetCommunicationException 若 gRPC 调用失败
     */
    public static void submit(WorkflowGraph graph, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            WorkflowServiceGrpc.WorkflowServiceBlockingStub stub =
                    WorkflowServiceGrpc.newBlockingStub(channel);

            SubmitWorkflowRequest request = SubmitWorkflowRequest.newBuilder()
                    .setGraph(graph)
                    .build();

            SubmitWorkflowResponse response = stub.submitWorkflow(request);

            if (response.getStatus() == SubmissionStatus.ACCEPTED) {
                LOG.log(Level.INFO, "Workflow submitted successfully: submissionId={0}, message={1}",
                        new Object[]{response.getSubmissionId(), response.getMessage()});
            } else {
                throw new IarnetSubmissionException(
                        "工作流提交被拒绝: " + response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            throw new IarnetCommunicationException(
                    "gRPC 调用失败: " + e.getStatus(), e);
        } finally {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }

    // ======== InputFlow ========

    private class InputFlow<T> implements Flow<T> {
        private final String inputParamName;
        private java.lang.reflect.Type inputType;

        private InputFlow(String inputParamName, java.lang.reflect.Type inputType) {
            this.inputParamName = inputParamName;
            this.inputType = inputType;
        }

        private Type ensureInputTypeAndRegister(TaskFunction<T, ?> function) {
            if (inputType == null) {
                inputType = TypeExtractor.extractOutputType(function, TaskFunction.class, 0);
            }
            if (inputType == null || inputType == Object.class) {
                throw new IarnetValidationException(
                        "无法推断输入参数 " + inputParamName + " 的类型，请使用 input(name, new TypeToken<YourType>() {}) 显式指定");
            }
            // 仅当类型为推断时才注册，显式 TypeToken 已在 input(name, TypeToken) 中注册
            if (!workflowInputs.stream().anyMatch(w -> inputParamName.equals(w.getName()))) {
                workflowInputs.add(WorkflowInput.newBuilder()
                        .setName(inputParamName)
                        .setType(com.kekwy.iarnet.proto.Types.fromType(inputType))
                        .build());
            }
            return com.kekwy.iarnet.proto.Types.fromType(inputType);
        }

        private Type ensureInputTypeAndRegisterFromHint(java.lang.reflect.Type hint) {
            if (hint != null && hint != Object.class) {
                inputType = hint;
            }
            if (inputType == null || inputType == Object.class) {
                throw new IarnetValidationException(
                        "无法推断输入参数 " + inputParamName + " 的类型，请使用 input(name, new TypeToken<YourType>() {}) 显式指定");
            }
            if (!workflowInputs.stream().anyMatch(w -> inputParamName.equals(w.getName()))) {
                workflowInputs.add(WorkflowInput.newBuilder()
                        .setName(inputParamName)
                        .setType(com.kekwy.iarnet.proto.Types.fromType(inputType))
                        .build());
            }
            return com.kekwy.iarnet.proto.Types.fromType(inputType);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            Type paramType = ensureInputTypeAndRegister(function);
            Node entry = addEntryNode(name, inputParamName, paramType, function, config);
            return new DefaultFlow<>(entry);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            java.lang.reflect.Type inType = TypeExtractor.extractOutputType(function, com.kekwy.iarnet.sdk.function.OutputFunction.class, 0);
            Type paramType = ensureInputTypeAndRegisterFromHint(inType);
            Node entry = addEntryNode(name, inputParamName, paramType, function, config);
            return new DefaultEndFlow<>(entry);
        }

        @Override
        public <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function) {
            return combine(name, other, function, null);
        }

        @Override
        public <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function, ExecutionConfig config) {
            throw new IarnetValidationException("input() 后请先调用 then() 添加入口节点，再进行 combine()");
        }

        @Override
        public ConditionalFlow<T> when(ConditionFunction<T> condition) {
            throw new IarnetValidationException("input() 后暂不支持 when()，请先 then() 再 when()");
        }

        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            inputType = typeHint.getType();
            return this;
        }
    }

    // ======== DefaultFlow ========

    private class DefaultFlow<T> implements Flow<T> {

        private final Node precursor;

        private DefaultFlow(Node precursor) {
            this.precursor = precursor;
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            return new DefaultFlow<>(addNode(name, precursor, function, config));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            return new DefaultEndFlow<>(addNode(name, precursor, function, config));
        }

        @Override
        public <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function) {
            return combine(name, other, function, null);
        }

        @Override
        public <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function, ExecutionConfig config) {
            if (other instanceof InputFlow<U>) {
                throw new IarnetValidationException("combine() 时另一 flow 须先 then() 添加入口节点，不能直接使用 input() 返回值");
            }
            if (!(other instanceof DefaultFlow<U> otherFlow)) {
                throw new IarnetValidationException("combine() 仅支持同一 workflow 中的 flow");
            }
            return new DefaultFlow<>(addCombineNode(name, precursor, otherFlow.precursor, function, config));
        }

        @Override
        public ConditionalFlow<T> when(ConditionFunction<T> condition) {
            return new DefaultConditionalFlow<>(precursor, condition);
        }


        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            nodeOutputTypeHints.put(precursor.getId(), typeHint.getType());
            return this;
        }

    }

    // ======== DefaultEndFlow ========

    private static class DefaultEndFlow<T> implements EndFlow<T> {
        @SuppressWarnings("FieldCanBeLocal")
        private final Node precursor; // 预留字段

        private DefaultEndFlow(Node precursor) {
            this.precursor = precursor;
        }
    }

    // ======== DefaultConditionalFlow ========

    private class DefaultConditionalFlow<T> implements ConditionalFlow<T> {
        private final Node precursor;
        private final ConditionFunction<T> condition;

        private DefaultConditionalFlow(Node precursor, ConditionFunction<T> condition) {
            this.precursor = precursor;
            this.condition = condition;
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            return new DefaultFlow<>(addNode(name, precursor, function, config, condition));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            return new DefaultEndFlow<>(addNode(name, precursor, function, config, condition));
        }
    }

    // ======================== 内部工具方法 ========================

    /** 生成带名称前缀的唯一节点 ID。 */
    private static String uniqueNodeId(String name) {
        return name + "-" + IDUtil.genUUID();
    }

    /** 添加普通节点（任务或输出），并建立与前驱的边。 */
    private Node addNode(String name, Node precursor,
                         Function function, ExecutionConfig config) {
        return addNode(name, precursor, function, config, null);
    }

    private Node addNode(String name, Node precursor,
                         Function function, ExecutionConfig config,
                         ConditionFunction<?> conditionFunction) {
        String nodeId = uniqueNodeId(name);
        Type inputType = precursor != null ? precursor.getFunction().getOutputType() : null;
        Type outputType = resolveOutputType(function, nodeId);

        Node node = Node.newBuilder()
                .setId(nodeId)
                .setName(name)
                .setFunction(buildFunctionDescriptor(function, inputType, outputType, nodeId))
                .setNodeConfig(buildNodeConfig(config))
                .setNodeKind(nodeKindFromFunction(function))
                .build();
        nodes.add(node);
        if (precursor != null) {
            Type conditionInputType = precursor.getFunction().getOutputType();
            int outputPort = conditionFunction != null
                    ? nextConditionalPort.merge(precursor.getId(), 1, Integer::sum)
                    : 0;
            edges.add(buildEdge(precursor.getId(), nodeId, conditionFunction, conditionInputType, outputPort, 0));
        }
        return node;
    }

    /** 添加入口节点（从工作流输入参数接收数据），无前驱、无边。 */
    private Node addEntryNode(String name, String inputParamName, Type inputType,
                              Function function, ExecutionConfig config) {
        String nodeId = uniqueNodeId(name);
        Type outputType = resolveOutputType(function, nodeId);
        Node node = Node.newBuilder()
                .setId(nodeId)
                .setName(name)
                .setFunction(buildFunctionDescriptor(function, inputType != null ? List.of(inputType) : List.of(), outputType, nodeId))
                .setNodeConfig(buildNodeConfig(config))
                .setNodeKind(nodeKindFromFunction(function))
                .build();
        nodes.add(node);
        nodeInputParamMap.put(nodeId, inputParamName);
        return node;
    }

    /** 添加合并节点，连接两个前驱。 */
    private Node addCombineNode(String name, Node precursor1, Node precursor2,
                               CombineFunction<?, ?, ?> function, ExecutionConfig config) {
        String nodeId = uniqueNodeId(name);
        Type inputType1 = precursor1.getFunction().getOutputType();
        Type inputType2 = precursor2.getFunction().getOutputType();
        Type outputType = resolveOutputType(function, nodeId);

        Node node = Node.newBuilder()
                .setId(nodeId)
                .setName(name)
                .setFunction(buildFunctionDescriptor(function, List.of(inputType1, inputType2), outputType, nodeId))
                .setNodeConfig(buildNodeConfig(config))
                .setNodeKind(NodeKind.NODE_KIND_COMBINE)
                .build();
        nodes.add(node);
        edges.add(buildEdge(precursor1.getId(), nodeId, null, inputType1, 0, 0));  // left
        edges.add(buildEdge(precursor2.getId(), nodeId, null, inputType2, 0, 1));  // right
        return node;
    }

    /** 收集工作流中使用的需外部源码的语言（Python、Go）。 */
    private static Set<Lang> collectExternalLangs(List<Node> nodes) {
        Set<Lang> result = new HashSet<>();
        for (Node node : nodes) {
            Lang lang = node.getFunction().getLang();
            if (lang == Lang.LANG_PYTHON || lang == Lang.LANG_GO) {
                result.add(lang);
            }
        }
        return result;
    }

    /**
     * 将约定目录 resource/function/{lang}/ 的内容复制到 ENV_EXTERNAL_SOURCE_DIR/{lang}/。
     * 每种语言只复制一次（进程内去重）。
     */
    private static void ensureExternalSourcesExported(Set<Lang> languages) {
        String baseDir = System.getenv(ENV_EXTERNAL_SOURCE_BASE_DIR);
        if (baseDir == null || baseDir.isBlank()) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_EXTERNAL_SOURCE_BASE_DIR + " 未设置，使用 Python/Go 外部函数时必须设置");
        }
        Path targetRoot = Path.of(baseDir);
        Path projectRoot = Path.of(System.getProperty("user.dir", "."));

        for (Lang lang : languages) {
            if (exportedLangs.contains(lang)) {
                continue;
            }
            String conventionDir = lang == Lang.LANG_PYTHON ? CONVENTION_PYTHON_DIR : CONVENTION_GO_DIR;
            String langSubdir = lang == Lang.LANG_PYTHON ? "python" : "go";
            Path source = projectRoot.resolve(conventionDir);
            Path target = targetRoot.resolve(langSubdir);

            if (!Files.isDirectory(source)) {
                LOG.log(Level.WARNING, "约定目录不存在，跳过导出: {0}", source.toAbsolutePath());
                continue;
            }
            try {
                Files.createDirectories(target);
                copyDirectory(source, target);
                exportedLangs.add(lang);
                LOG.log(Level.FINE, "已导出外部源码: {0} -> {1}", new Object[]{source, target});
            } catch (IOException e) {
                throw new IarnetConfigurationException("导出外部源码失败: " + source + " -> " + target, e);
            }
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(dir);
                Path dest = target.resolve(rel);
                if (!rel.toString().isEmpty()) {
                    Files.createDirectories(dest);
                }
                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Path rel = source.relativize(file);
                Path dest = target.resolve(rel);
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 构建边，可选附带条件函数；output_port/input_port 由 SDK 统一分配。 */
    private static Edge buildEdge(String fromNodeId, String toNodeId,
                                  ConditionFunction<?> conditionFunction, Type conditionInputType,
                                  int outputPort, int inputPort) {
        FunctionDescriptor conditionFn = conditionFunction != null
                ? buildFunctionDescriptorForCondition(conditionFunction, conditionInputType)
                : FunctionDescriptor.getDefaultInstance();
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
                .setConditionFunction(conditionFn)
                .setOutputPort(outputPort)
                .setInputPort(inputPort)
                .build();
    }

    /** 将 ExecutionConfig 转为 proto NodeConfig。 */
    private static NodeConfig buildNodeConfig(ExecutionConfig config) {
        if (config == null) {
            config = ExecutionConfig.of();
        }
        return NodeConfig.newBuilder()
                .setReplicas(config.getReplicas())
                .setResourceSpec(config.getResourceSpec())
                .build();
    }

    /** 根据函数类型返回 NodeKind（Task/Output，Combine 在 addCombineNode 中直接设置）。 */
    private static NodeKind nodeKindFromFunction(Function function) {
        if (function instanceof OutputFunction) {
            return NodeKind.NODE_KIND_OUTPUT;
        }
        if (function instanceof TaskFunction || function instanceof PythonTaskFunction || function instanceof GoTaskFunction) {
            return NodeKind.NODE_KIND_TASK;
        }
        return NodeKind.NODE_KIND_UNSPECIFIED;
    }

    /**
     * 解析节点输出类型。
     * <p>
     * 优先级：Python/Go 的 outputTypeHint > TypeExtractor 反射 > returns() 提示。
     * OutputFunction 无输出，返回 null。
     *
     * @param nodeId 节点 ID，用于查找 returns() 提示
     * @return proto Type，无法解析时返回 null（由 finalizeNodesWithTypes 检查）
     */
    private Type resolveOutputType(Function function, String nodeId) {
        if (function instanceof OutputFunction) {
            return null;
        }
        java.lang.reflect.Type extracted = null;
        if (function instanceof PythonTaskFunction<?, ?> fn && fn.getOutputTypeHint() != null) {
            extracted = fn.getOutputTypeHint();
        } else if (function instanceof GoTaskFunction<?, ?> fn && fn.getOutputTypeHint() != null) {
            extracted = fn.getOutputTypeHint();
        } else if (function instanceof TaskFunction<?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, TaskFunction.class, 1);
        } else if (function instanceof CombineFunction<?, ?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, CombineFunction.class, 2);
        }
        if (extracted != null && extracted != Object.class) {
            return com.kekwy.iarnet.proto.Types.fromType(extracted);
        }
        java.lang.reflect.Type hint = nodeOutputTypeHints.get(nodeId);
        if (hint != null) {
            return com.kekwy.iarnet.proto.Types.fromType(hint);
        }
        return null; // 交由 finalizeNodesWithTypes 检查并抛错
    }

    /** 构建 FunctionDescriptor，支持 Java 序列化、Python、Go 三种形式。 */
    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              Type inputType, Type outputType, String nodeId) {
        return buildFunctionDescriptor(function, inputType != null ? List.of(inputType) : List.of(), outputType, nodeId);
    }

    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              List<Type> inputTypes, Type outputType, String nodeId) {

        if (function.getLang() == Lang.LANG_UNSPECIFIED) {
            throw new IarnetValidationException("不支持的函数语言: " + function.getLang());
        }

        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(function.getLang());

        if (function instanceof PythonTaskFunction<?, ?> fn) {
            b.setFunctionIdentifier(fn.getFunctionIdentifier());
        } else if (function instanceof GoTaskFunction<?, ?> fn) {
            b.setFunctionIdentifier(fn.getFunctionIdentifier());
        } else if (function.getLang() == Lang.LANG_JAVA) {
            b.setFunctionIdentifier(function.getClass().getName())
                    .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(function)));
        }

        for (Type t : inputTypes) {
            b.addInputsType(t);
        }
        if (outputType != null) {
            b.setOutputType(outputType);
        }
        return b.build();
    }

    /** 构建边的条件函数描述符（input 来自前驱输出，output 为 boolean）。 */
    private static FunctionDescriptor buildFunctionDescriptorForCondition(ConditionFunction<?> condition, Type inputType) {
        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(condition.getLang())
                .setFunctionIdentifier(condition.getClass().getName())
                .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(condition)));
        if (inputType != null) {
            b.addInputsType(inputType);
        }
        b.setOutputType(com.kekwy.iarnet.proto.Types.BOOLEAN);
        return b.build();
    }
}
