package com.kekwy.iarnet.adapter.engine;

import com.kekwy.iarnet.proto.adapter.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

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

    /**
     * 部署一个实例。
     *
     * @param request           部署请求（可含 artifact_url，由调用方先拉取后再传 artifactLocalPath）
     * @param artifactLocalPath 已拉取到本地的 artifact 文件路径；为 null 表示无 artifact 或由其他方式提供
     */
    DeployInstanceResponse deployInstance(DeployInstanceRequest request, Path artifactLocalPath);

    /** 部署一个实例（无本地 artifact，兼容旧调用） */
    default DeployInstanceResponse deployInstance(DeployInstanceRequest request) {
        return deployInstance(request, null);
    }

    /** 停止一个实例 */
    StopInstanceResponse stopInstance(String instanceId);

    /** 移除一个实例 */
    RemoveInstanceResponse removeInstance(String instanceId);

    /** 查询实例状态 */
    GetInstanceStatusResponse getInstanceStatus(String instanceId);

    /** 获取设备实时资源使用情况 */
    GetResourceUsageResponse getResourceUsage();
}
