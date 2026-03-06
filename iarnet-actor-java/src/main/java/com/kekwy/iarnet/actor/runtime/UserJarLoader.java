package com.kekwy.iarnet.actor.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 根据环境变量加载用户 JAR，并通过反射解析出 Actor 要执行的函数，封装为 {@link JavaInvokeHandler}。
 * <p>
 * 环境变量：
 * <ul>
 *   <li>{@code IARNET_ACTOR_JAR}：用户 JAR 的绝对或相对路径，必填（若未设置则返回 null）</li>
 *   <li>{@code IARNET_ACTOR_CLASS}：完整类名，必填</li>
 *   <li>{@code IARNET_ACTOR_METHOD}：方法名，必填。方法签名须为 {@code byte[] method(byte[])}（静态或实例方法）</li>
 * </ul>
 */
public final class UserJarLoader {

    private static final Logger log = LoggerFactory.getLogger(UserJarLoader.class);

    public static final String ENV_JAR = "IARNET_ACTOR_JAR";
    public static final String ENV_CLASS = "IARNET_ACTOR_CLASS";
    public static final String ENV_METHOD = "IARNET_ACTOR_METHOD";

    private UserJarLoader() {}

    /**
     * 从环境变量读取 JAR 路径、类名、方法名，加载类并通过反射获取方法，返回对应的 Handler。
     * 若任一环境变量未设置或加载/反射失败，返回 null（调用方应回退到默认 Handler）。
     */
    public static JavaInvokeHandler loadFromEnv() {
        String jarPath = System.getenv(ENV_JAR);
        String className = System.getenv(ENV_CLASS);
        String methodName = System.getenv(ENV_METHOD);

        if (jarPath == null || jarPath.isBlank()) {
            log.debug("{} 未设置，跳过用户 JAR 加载", ENV_JAR);
            return null;
        }
        if (className == null || className.isBlank()) {
            log.warn("{} 未设置，无法从用户 JAR 解析函数", ENV_CLASS);
            return null;
        }
        if (methodName == null || methodName.isBlank()) {
            log.warn("{} 未设置，无法从用户 JAR 解析函数", ENV_METHOD);
            return null;
        }

        Path path = Paths.get(jarPath).toAbsolutePath();
        if (!path.toFile().exists() || !path.toFile().isFile()) {
            log.error("用户 JAR 不存在或不是文件: {}", path);
            return null;
        }

        try {
            URL jarUrl = path.toUri().toURL();
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, UserJarLoader.class.getClassLoader())) {
                Class<?> clazz = Class.forName(className.trim(), true, loader);
                Method method = resolveMethod(clazz, methodName.trim());
                if (method == null) {
                    log.error("在类 {} 中未找到符合 byte[] method(byte[]) 的方法: {}", className, methodName);
                    return null;
                }

                Object target = null;
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    target = clazz.getDeclaredConstructor().newInstance();
                }

                log.info("已从用户 JAR 加载函数: class={}, method={}, static={}",
                        className, methodName, target == null);
                return new ReflectionInvokeHandler(method, target);
            }
        } catch (MalformedURLException e) {
            log.error("无效的 JAR 路径: {}", jarPath, e);
            return null;
        } catch (ClassNotFoundException e) {
            log.error("在 JAR 中未找到类: {}", className, e);
            return null;
        } catch (NoSuchMethodException e) {
            log.error("未找到匹配的 method(byte[]) 或无参构造: class={}, method={}", className, methodName, e);
            return null;
        } catch (Exception e) {
            log.error("加载用户 JAR 或反射创建 Handler 失败: jar={}, class={}, method={}",
                    jarPath, className, methodName, e);
            return null;
        }
    }

    /**
     * 查找签名 byte[] method(byte[]) 的静态或实例方法。
     */
    private static Method resolveMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || params[0] != byte[].class) {
                continue;
            }
            if (m.getReturnType() != byte[].class) {
                continue;
            }
            return m;
        }
        return null;
    }
}
