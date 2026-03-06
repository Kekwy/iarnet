package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.ir.FunctionDescriptor;
import com.kekwy.iarnet.proto.ir.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 从 IR 的 {@link FunctionDescriptor} 加载并构建 {@link JavaInvokeHandler}。
 * <p>
 * 加载策略（与 workflow.proto 约定一致）：
 * <ul>
 *   <li>若 {@code serialized_function} 非空：按 Java 序列化反序列化，得到对象后查找单参方法（如 apply）并包装为 Handler。</li>
 *   <li>否则使用 {@code artifact_path}（JAR）+ {@code function_identifier}（类名或 "类名#方法名"）通过反射加载。</li>
 * </ul>
 */
public final class FunctionDescriptorLoader {

    private static final Logger log = LoggerFactory.getLogger(FunctionDescriptorLoader.class);

    private FunctionDescriptorLoader() {}

    /**
     * 从文件加载：文件内容为 {@link FunctionDescriptor} 的 proto 二进制。
     * 用于控制平面在启动 Actor 前将函数描述写入挂载路径，Actor 启动时即可读取，无需等待 gRPC 下发。
     *
     * @param filePath 文件路径（由环境变量 IARNET_ACTOR_FUNCTION_FILE 等传入）
     * @return 加载成功返回 Handler，否则 null
     */
    public static JavaInvokeHandler fromFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Paths.get(filePath).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            log.warn("函数描述文件不存在或不是普通文件: {}", path);
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            FunctionDescriptor fd = FunctionDescriptor.parseFrom(bytes);
            return fromDescriptor(fd);
        } catch (IOException e) {
            log.error("读取或解析函数描述文件失败: {}", path, e);
            return null;
        } catch (Exception e) {
            log.error("解析 FunctionDescriptor 失败: {}", path, e);
            return null;
        }
    }

    /**
     * 根据 proto FunctionDescriptor 构建 Handler。仅支持 {@link Lang#LANG_JAVA}；失败返回 null。
     */
    public static JavaInvokeHandler fromDescriptor(FunctionDescriptor fd) {
        if (fd == null || fd.getLang() != Lang.LANG_JAVA) {
            log.warn("仅支持 LANG_JAVA 的 FunctionDescriptor，当前 lang={}", fd != null ? fd.getLang() : null);
            return null;
        }

        String artifactPath = resolveArtifactPath(fd);

        if (fd.getSerializedFunction() != null && !fd.getSerializedFunction().isEmpty()) {
            return loadFromSerializedFunction(fd.getSerializedFunction().toByteArray(), artifactPath);
        }

        String identifier = fd.getFunctionIdentifier();
        if (artifactPath == null || artifactPath.isBlank() || identifier == null || identifier.isBlank()) {
            log.warn("artifact_path 或 function_identifier 为空，无法从 JAR 加载");
            return null;
        }

        return loadFromArtifact(artifactPath, identifier);
    }

    /**
     * 解析容器内可用的 artifact 路径：
     * 1) 优先使用 IARNET_ARTIFACT_PATH（Adapter 注入的容器内 JAR 路径）；
     * 2) 其次回退到 IARNET_ACTOR_JAR（手动运行 Actor 时可设置）。
     * <p>
     * 注意：fd.getArtifactPath() 是控制平面侧的缓存路径，在容器内不可用，不使用。
     */
    private static String resolveArtifactPath(FunctionDescriptor fd) {
        String envArtifact = System.getenv("IARNET_ARTIFACT_PATH");
        if (envArtifact != null && !envArtifact.isBlank()) {
            return envArtifact;
        }
        String envJar = System.getenv(UserJarLoader.ENV_JAR);
        if (envJar != null && !envJar.isBlank()) {
            return envJar;
        }
        return null;
    }

    /**
     * 从 serialized_function 字节反序列化得到对象，查找单参方法后包装为 Handler。
     * 若提供了 artifactPath，则使用包含用户 JAR 的 ClassLoader 进行反序列化，
     * 以解析 lambda 捕获的定义类。
     */
    private static JavaInvokeHandler loadFromSerializedFunction(byte[] serialized, String artifactPath) {
        URLClassLoader jarLoader = null;
        try {
            jarLoader = buildJarClassLoader(artifactPath);
            ObjectInputStream ois = jarLoader != null
                    ? new ClassLoaderObjectInputStream(new ByteArrayInputStream(serialized), jarLoader)
                    : new ObjectInputStream(new ByteArrayInputStream(serialized));

            Object udf = ois.readObject();
            ois.close();

            Method m = SerializedFunctionInvokeHandler.findSingleArgMethod(udf);
            if (m == null) {
                log.error("反序列化得到的对象未找到单参非 void 方法: {}", udf.getClass().getName());
                return null;
            }
            log.info("已从 serialized_function 加载函数: class={}, method={}",
                    udf.getClass().getName(), m.getName());
            return new SerializedFunctionInvokeHandler(udf, m);
        } catch (Exception e) {
            log.error("反序列化 serialized_function 失败", e);
            return null;
        }
        // 注意：此处不关闭 jarLoader，因为反序列化出来的 UDF 对象仍依赖该 ClassLoader
    }

    private static URLClassLoader buildJarClassLoader(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            return null;
        }
        Path path = Paths.get(artifactPath).toAbsolutePath();
        if (!path.toFile().exists() || !path.toFile().isFile()) {
            log.warn("artifact 不存在或不是文件，反序列化将使用默认 ClassLoader: {}", path);
            return null;
        }
        try {
            URL jarUrl = path.toUri().toURL();
            return new URLClassLoader(new URL[]{jarUrl}, FunctionDescriptorLoader.class.getClassLoader());
        } catch (Exception e) {
            log.warn("构建 JAR ClassLoader 失败: {}", artifactPath, e);
            return null;
        }
    }

    /**
     * 使用指定 ClassLoader 解析类的 ObjectInputStream，
     * 用于在用户 JAR 的 ClassLoader 下反序列化 lambda / 函数对象。
     */
    private static class ClassLoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader classLoader;

        ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, classLoader);
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }

    /**
     * 从 artifact_path（JAR）与 function_identifier（类名 或 类名#方法名）反射加载。
     */
    private static JavaInvokeHandler loadFromArtifact(String artifactPath, String functionIdentifier) {
        Path path = Paths.get(artifactPath).toAbsolutePath();
        if (!path.toFile().exists() || !path.toFile().isFile()) {
            log.error("artifact 不存在或不是文件: {}", path);
            return null;
        }

        String className;
        String methodName;
        int hash = functionIdentifier.indexOf('#');
        if (hash >= 0) {
            className = functionIdentifier.substring(0, hash).trim();
            methodName = functionIdentifier.substring(hash + 1).trim();
        } else {
            className = functionIdentifier.trim();
            methodName = "apply"; // 与 MapFunction 等约定一致
        }

        try {
            URL jarUrl = path.toUri().toURL();
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, FunctionDescriptorLoader.class.getClassLoader())) {
                Class<?> clazz = Class.forName(className, true, loader);
                Method method = resolveByteArrayMethod(clazz, methodName);
                if (method == null) {
                    log.error("在类 {} 中未找到符合 byte[] method(byte[]) 的 {}", className, methodName);
                    return null;
                }

                Object target = null;
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    target = clazz.getDeclaredConstructor().newInstance();
                }

                log.info("已从 artifact 加载函数: class={}, method={}", className, methodName);
                return new ReflectionInvokeHandler(method, target);
            }
        } catch (Exception e) {
            log.error("从 artifact 加载失败: path={}, identifier={}", artifactPath, functionIdentifier, e);
            return null;
        }
    }

    private static Method resolveByteArrayMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || params[0] != byte[].class) continue;
            if (m.getReturnType() != byte[].class) continue;
            return m;
        }
        return null;
    }
}
