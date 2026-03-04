package com.kekwy.iarnet.adapter.engine;

import com.kekwy.iarnet.proto.adapter.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * 资源适配器核心 SPI 接口。
 * <p>
 * 每种运行时（Docker / K8s / 裸进程）提供一个实现。
 * gRPC 服务层通过该接口将请求委托给具体引擎处理。
 */
public interface AdapterEngine extends AutoCloseable {

    /** 适配器类型标识，如 "docker"、"k8s"、"process" */
    String adapterType();

    /** 获取设备信息与资源容量 */
    GetDeviceInfoResponse getDeviceInfo();

    /** 接收 artifact 流并存储到本地，返回存储路径 */
    TransferArtifactResponse transferArtifact(String artifactId, String fileName, InputStream data) throws IOException;

    /** 部署一个实例 */
    DeployInstanceResponse deployInstance(DeployInstanceRequest request);

    /** 停止一个实例 */
    StopInstanceResponse stopInstance(String instanceId);

    /** 移除一个实例 */
    RemoveInstanceResponse removeInstance(String instanceId);

    /** 查询实例状态 */
    GetInstanceStatusResponse getInstanceStatus(String instanceId);

    /** 获取设备实时资源使用情况 */
    GetResourceUsageResponse getResourceUsage();
}
