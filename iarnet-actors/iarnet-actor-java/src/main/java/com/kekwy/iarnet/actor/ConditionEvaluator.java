package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 条件函数评估：根据反序列化后的条件函数与输入值，返回满足条件的 output_port 列表。
 * 无条件函数时返回 [0]（默认端口）。
 */
public final class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private final Map<Integer, Object> portToCondition;
    private final Map<Integer, Type> portToInputType;

    public ConditionEvaluator(Map<Integer, Object> portToCondition, Map<Integer, Type> portToInputType) {
        this.portToCondition = portToCondition != null ? portToCondition : Map.of();
        this.portToInputType = portToInputType != null ? portToInputType : Map.of();
    }

    /**
     * 对给定值（已解码的 Java 对象）评估所有条件函数，返回满足条件的 output_port 列表。
     * 若没有任何条件函数，返回 [0]。
     */
    public List<Integer> evaluate(Object value) {
        if (portToCondition.isEmpty()) {
            return List.of(0);
        }
        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, Object> e : portToCondition.entrySet()) {
            int port = e.getKey();
            Object condition = e.getValue();
            try {
                Method test = condition.getClass().getMethod("test", Object.class);
                Boolean pass = (Boolean) test.invoke(condition, value);
                if (Boolean.TRUE.equals(pass)) {
                    result.add(port);
                }
            } catch (Exception ex) {
                log.warn("条件函数评估失败: port={}", port, ex);
            }
        }
        if (result.isEmpty()) {
            result.add(0);
        }
        return result;
    }

    /**
     * 从 FunctionDescriptor 条件映射与 UserJarLoader 构建 ConditionEvaluator。
     *
     * @param conditionDescriptors port -> FunctionDescriptor（条件函数）
     * @param jarLoader             用于反序列化条件函数
     */
    public static ConditionEvaluator fromDescriptors(
            Map<Integer, FunctionDescriptor> conditionDescriptors,
            UserJarLoader jarLoader) throws IOException, ClassNotFoundException {
        if (conditionDescriptors == null || conditionDescriptors.isEmpty()) {
            return new ConditionEvaluator(Map.of(), Map.of());
        }
        Map<Integer, Object> portToCondition = new java.util.HashMap<>();
        Map<Integer, Type> portToInputType = new java.util.HashMap<>();
        for (Map.Entry<Integer, FunctionDescriptor> e : conditionDescriptors.entrySet()) {
            int port = e.getKey();
            FunctionDescriptor fd = e.getValue();
            if (fd.getSerializedFunction().isEmpty()) {
                continue;
            }
            Object fn = jarLoader.deserialize(fd.getSerializedFunction().toByteArray());
            portToCondition.put(port, fn);
            if (fd.getInputsTypeCount() > 0) {
                portToInputType.put(port, fd.getInputsType(0));
            }
        }
        return new ConditionEvaluator(portToCondition, portToInputType);
    }
}
