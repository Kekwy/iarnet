package com.kekwy.iarnet.application.executor;

import com.kekwy.iarnet.application.executor.packager.JavaPackager;
import com.kekwy.iarnet.application.executor.packager.Packager;
import com.kekwy.iarnet.application.executor.packager.PythonPackager;
import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.proto.ir.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 访问 WorkflowGraph 中的每个节点，为 Operator 节点准备 artifact：
 * <ul>
 *   <li>若 {@code artifact_path} 为空 → 函数来自 DSL 提交方自身 JAR，
 *       从 Workspace 的 artifacts/ 目录获取已有产物路径</li>
 *   <li>若 {@code artifact_path} 不为空 → 函数来自外部源码目录，
 *       根据语言调用 {@link JavaPackager} 或 {@link PythonPackager} 打包，
 *       产物存入 Workspace 的 artifacts/ 目录</li>
 * </ul>
 *
 * <p>处理结束后，通过 {@link #getNodeArtifacts()} 获取 nodeId → artifact 路径的映射。
 */
public class ArtifactPrepareVisitor implements ProtoNodeVisitor {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPrepareVisitor.class);

    private final Workspace workspace;
    private final Map<Lang, Packager> packagers;

    /** nodeId → 该节点可用的 artifact 文件路径 */
    private final Map<String, Path> nodeArtifacts = new HashMap<>();

    public ArtifactPrepareVisitor(Workspace workspace) {
        this.workspace = workspace;
        this.packagers = Map.of(
                Lang.LANG_JAVA, new JavaPackager(),
                Lang.LANG_PYTHON, new PythonPackager()
        );
    }

    /**
     * @return nodeId → artifact 路径的不可变映射
     */
    public Map<String, Path> getNodeArtifacts() {
        return Map.copyOf(nodeArtifacts);
    }

    // ====================== Source ======================

    @Override
    public void visitSource(Node node, SourceNodeDetail detail) {
        log.debug("Source 节点无需 artifact 准备: nodeId={}, kind={}", node.getId(), detail.getSourceKind());
    }

    // ====================== Operator ======================

    @Override
    public void visitOperator(Node node, OperatorNodeDetail detail) {
        // UNION 等内建算子本身不携带用户函数，不需要 artifact。
        // 目前 UNION 在 proto 中被映射为 OPERATOR_KIND_UNSPECIFIED。
        if (detail.getOperatorKind() == OperatorKind.OPERATOR_KIND_UNSPECIFIED) {
            log.debug("内建算子（如 UNION）无需 artifact 准备: nodeId={}", node.getId());
            return;
        }

        FunctionDescriptor fd = detail.getFunction();
        String nodeId = node.getId();
        String artifactPath = fd.getArtifactPath();
        Lang lang = fd.getLang();

        if (artifactPath.isEmpty()) {
            resolveExistingArtifact(nodeId, lang);
        } else {
            packFromSource(nodeId, lang, Path.of(artifactPath));
        }
    }

    // ====================== Sink ======================

    @Override
    public void visitSink(Node node, SinkNodeDetail detail) {
        log.debug("Sink 节点无需 artifact 准备: nodeId={}, kind={}", node.getId(), detail.getSinkKind());
    }

    // ====================== 内部方法 ======================

    /**
     * 函数来自 DSL 提交方自身运行时（artifact_path 为空），
     * 从 Workspace 的 artifacts/ 目录查找已有产物。
     */
    private void resolveExistingArtifact(String nodeId, Lang lang) {
        Path artifactDir = workspace.getArtifactDir();
        String suffix = (lang == Lang.LANG_PYTHON) ? ".tar.gz" : ".jar";

        try (Stream<Path> files = Files.list(artifactDir)) {
            Path artifact = files
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Workspace artifacts/ 中未找到 " + suffix + " 文件: " + artifactDir));

            nodeArtifacts.put(nodeId, artifact);
            log.info("节点 {} 使用已有 artifact: {}", nodeId, artifact);
        } catch (IOException e) {
            throw new RuntimeException("扫描 artifact 目录失败: " + artifactDir, e);
        }
    }

    /**
     * 函数来自外部源码目录（artifact_path 不为空），
     * 根据语言打包并将产物放入 Workspace 的 artifacts/ 目录。
     */
    private void packFromSource(String nodeId, Lang lang, Path sourcePath) {
        Packager packager = packagers.get(lang);
        if (packager == null) {
            throw new UnsupportedOperationException("不支持的语言打包: " + lang);
        }

        Path outputDir = workspace.getArtifactDir();
        log.info("节点 {} 开始打包: lang={}, source={}", nodeId, lang, sourcePath);

        Path artifact = packager.pack(sourcePath, outputDir);
        nodeArtifacts.put(nodeId, artifact);
        log.info("节点 {} 打包完成: {}", nodeId, artifact);
    }
}
