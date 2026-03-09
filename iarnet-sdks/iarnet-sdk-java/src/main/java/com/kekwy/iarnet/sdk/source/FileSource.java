package com.kekwy.iarnet.sdk.source;

import com.kekwy.iarnet.sdk.converter.SourceVisitor;

import java.nio.file.Path;

/**
 * 从文件按行读取的字符串数据源。
 */
public final class FileSource implements Source<String> {

    private final Path path;

    private FileSource(Path path) {
        this.path = path;
    }

    public static FileSource of(String path) {
        return new FileSource(Path.of(path));
    }

    public static FileSource of(Path path) {
        return new FileSource(path);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public <R> R accept(SourceVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
