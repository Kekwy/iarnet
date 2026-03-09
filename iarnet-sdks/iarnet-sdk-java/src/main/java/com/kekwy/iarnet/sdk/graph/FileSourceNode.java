package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.Type;

import java.nio.file.Path;

public class FileSourceNode extends SourceNode {

    private final Path path;

    public FileSourceNode(String id, Type outputType, Path path) {
        super(id, outputType);
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
