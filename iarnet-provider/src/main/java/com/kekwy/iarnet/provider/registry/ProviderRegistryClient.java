package com.kekwy.iarnet.provider.registry;

import com.kekwy.iarnet.provider.config.ProviderIdentity;
import com.kekwy.iarnet.provider.config.ProviderProperties;
import com.kekwy.iarnet.provider.control.ControlService;
import com.kekwy.iarnet.provider.deployment.DeploymentService;
import com.kekwy.iarnet.provider.signaling.SignalingService;
import com.kekwy.iarnet.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.iarnet.proto.provider.RegisterProviderRequest;
import com.kekwy.iarnet.proto.provider.RegisterProviderResponse;
import io.grpc.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Provider 注册客户端：仅负责 RegisterProvider(unary)；成功后写入 {@link ProviderIdentity}，
 * 并打开 Control / Deployment / Signaling 通道（各 Service 从 ProviderIdentity 读取 providerId）。
 */
public class ProviderRegistryClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistryClient.class);

    public static final Metadata.Key<String> PROVIDER_ID_METADATA_KEY =
            Metadata.Key.of("provider-id", Metadata.ASCII_STRING_MARSHALLER);

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub blockingStub;
    private final ProviderProperties props;
    private final ProviderIdentity identity;

    private volatile boolean closed = false;

    private final ControlService controlService;
    private final DeploymentService deploymentService;
    private final SignalingService signalingService;

    public ProviderRegistryClient(
            ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub blockingStub,
            ProviderProperties props,
            ProviderIdentity identity,
            ControlService controlService,
            DeploymentService deploymentService,
            SignalingService signalingService) {
        this.blockingStub = blockingStub;
        this.props = props;
        this.identity = identity;
        this.controlService = controlService;
        this.deploymentService = deploymentService;
        this.signalingService = signalingService;
    }

    public String getProviderId() {
        return identity != null ? identity.getProviderId() : null;
    }

    /**
     * 执行注册并打开 Control / Deployment / Signaling 通道；注册失败时不打开通道。
     */
    public void start() {
        String name = props.getName() != null ? props.getName() : "provider";
        String description = props.getDescription() != null ? props.getDescription() : "";
        String zone = props.getZone() != null ? props.getZone() : "";
        String type = props.getType() != null ? props.getType() : "docker";
        List<String> tags = props.getTags() != null ? props.getTags() : List.of();

        RegisterProviderRequest request = RegisterProviderRequest.newBuilder()
                .setProviderName(name)
                .setProviderType(type)
                .setDescription(description)
                .setZone(zone)
                .addAllTags(tags)
                .build();

        try {
            RegisterProviderResponse response = blockingStub.registerProvider(request);
            if (response.getAccepted()) {
                String providerId = response.getProviderId();
                identity.setProviderId(providerId);
                log.info("Provider 注册成功: providerId={}, name={}", providerId, name);

                controlService.openChannel();
                deploymentService.openChannel();
                signalingService.openChannel();
            } else {
                log.error("Provider 注册被拒绝: name={}, message={}", name, response.getMessage());
            }
        } catch (Exception e) {
            log.error("Provider 注册失败: name={}", name, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        controlService.close();
        deploymentService.close();
        signalingService.close();
    }
}
