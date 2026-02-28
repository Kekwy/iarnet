package com.kekwy.iarnet.api.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

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

    @Override
    public Iterator<String> iterator() {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
