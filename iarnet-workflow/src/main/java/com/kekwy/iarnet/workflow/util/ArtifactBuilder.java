package com.kekwy.iarnet.workflow.util;

import com.kekwy.iarnet.common.packager.GoPackager;
import com.kekwy.iarnet.common.packager.JavaPackager;
import com.kekwy.iarnet.common.packager.Packager;
import com.kekwy.iarnet.common.packager.PythonPackager;
import com.kekwy.iarnet.proto.common.Lang;

import java.nio.file.Path;
import java.util.Map;

/**
 * 跨语言 artifact 构建器：根据源码目录构建 Java fat jar、Python conda 环境 tar.xz、Go 可执行文件。
 * 委托给 common 模块中的各语言 Packager 实现。
 */
public class ArtifactBuilder {

    private static final Map<Lang, Packager> PACKAGERS = Map.of(
            Lang.LANG_JAVA, new JavaPackager(),
            Lang.LANG_PYTHON, new PythonPackager(),
            Lang.LANG_GO, new GoPackager()
    );

    /**
     * 根据语言将 sourcePath 下的源码打包为 artifact，输出到 artifactPath。
     *
     * @param lang         语言类型
     * @param sourcePath   源码目录
     * @param artifactPath 输出 artifact 路径
     */
    public static void buildArtifact(Lang lang, Path sourcePath, Path artifactPath) {
        Packager packager = PACKAGERS.get(lang);
        if (packager == null) {
            throw new IllegalStateException("不支持的语言: " + lang);
        }
        Path outputDir = artifactPath.getParent();
        packager.pack(sourcePath, outputDir);
    }
}
