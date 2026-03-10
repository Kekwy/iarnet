package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.OutputFunction;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工作流输出（Sink）DSL 的静态工厂。
 * <p>
 * 提供常用的 {@link OutputFunction} 实现，用于工作流末端消费上游数据。
 * 与 {@link com.kekwy.iarnet.sdk.Workflow} 的 {@code flow.then("sink", Outputs.println())} 等用法配合。
 * <p>
 * 典型用法：
 * <pre>{@code
 * w.input("src", Inputs.of(1, 2, 3))
 *  .then("double", x -> x * 2)
 *  .then("print", Outputs.println());   // 打印到 stdout
 *
 * w.input("src", ...)
 *  .then("noop", Outputs.noop());      // 丢弃结果（用于测试或占位）
 * }</pre>
 */
public final class Outputs {

    private static final Logger LOG = Logger.getLogger(Outputs.class.getName());

    private Outputs() {
        // 工具类，禁止实例化
    }

    /**
     * 返回一个将每个输入元素打印到标准输出的 OutputFunction。
     * <p>
     * 等价于 {@code value -> System.out.println(value)}，便于调试和示例代码。
     *
     * @param <T> 输入元素类型
     * @return 将输入打印到 stdout 的 OutputFunction
     */
    public static <T> OutputFunction<T> println() {
        LOG.log(Level.FINE, "Output sink created: println (stdout)");
        return value -> {
            LOG.log(Level.FINEST, "Output (println): {0}", value);
            System.out.println(value);
        };
    }

    /**
     * 返回一个丢弃所有输入的 OutputFunction（空操作）。
     * <p>
     * 适用于测试占位、或不需要消费结果的场景。
     *
     * @param <T> 输入元素类型
     * @return 不执行任何操作的 OutputFunction
     */
    public static <T> OutputFunction<T> noop() {
        LOG.log(Level.FINE, "Output sink created: noop");
        return value -> {};
    }
}
