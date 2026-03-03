package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

import java.nio.file.Path;

public class FileSourceNode extends SourceNode {

    private final Path path;

    public FileSourceNode(String id, DataType outputType, Path path) {
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
