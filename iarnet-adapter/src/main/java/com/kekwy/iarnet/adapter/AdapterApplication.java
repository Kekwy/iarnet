package com.kekwy.iarnet.adapter;

import com.kekwy.iarnet.adapter.agent.LocalActorGraph;
import com.kekwy.iarnet.adapter.agent.LocalAgentServiceImpl;
import com.kekwy.iarnet.adapter.artifact.ArtifactFetcher;
import com.kekwy.iarnet.adapter.artifact.ArtifactStore;
import com.kekwy.iarnet.adapter.config.AdapterProperties;
import com.kekwy.iarnet.adapter.engine.AdapterEngine;
import com.kekwy.iarnet.adapter.engine.docker.DockerEngine;
import com.kekwy.iarnet.adapter.engine.k8s.KubernetesEngine;
import com.kekwy.iarnet.adapter.registry.RegistryClient;
import jakarta.annotation.PreDestroy;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;

/**
 * Adapter 启动入口。
 * <p>
 * 启动后通过 CommandChannel 双向流与 control-plane 通信，
 * 自身不暴露任何端口，所有连接均由 Adapter 主动发起。
 */
@SpringBootApplication
@EnableConfigurationProperties(AdapterProperties.class)
public class AdapterApplication {

    private static final Logger log = LoggerFactory.getLogger(AdapterApplication.class);

    private final AdapterProperties props;

    private AdapterEngine engine;
    private RegistryClient registryClient;
    private Server localAgentServer;
    private ManagedChannel signalingChannel;

    public AdapterApplication(AdapterProperties props) {
        this.props = props;
    }

    public static void main(String[] args) {
        SpringApplication.run(AdapterApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ArtifactStore artifactStore = new ArtifactStore(Path.of(props.getArtifactDir()));

        com.kekwy.iarnet.proto.ir.Resource totalResource =
                com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                        .setCpu(props.getResource().getCpu())
                        .setMemory(props.getResource().getMemory())
                        .setGpu(props.getResource().getGpu())
                        .build();

        engine = createEngine(props, totalResource, artifactStore);
        ArtifactFetcher artifactFetcher = new ArtifactFetcher(artifactStore);

        // 启动本地 Device Agent（基础版），监听在固定端口，供 Actor 回连
        int agentPort = props.getDeviceAgent().getPort();
        try {
            LocalAgentServiceImpl localAgentService = new LocalAgentServiceImpl();
            localAgentServer = ServerBuilder.forPort(agentPort)
                    .addService(localAgentService)
                    .build()
                    .start();
            log.info("Local Device Agent 已启动: port={}", agentPort);
        } catch (Exception e) {
            throw new IllegalStateException("启动本地 Device Agent 失败: port=" + agentPort, e);
        }

        var cp = props.getControlPlane();
        registryClient = new RegistryClient(
                props.getName(), props.getDescription(), engine, artifactFetcher,
                props.getTags(),
                cp.getHost(), cp.getPort());
        registryClient.start();

        // 建立 DeviceAgentRegistryService.SignalingChannel，用于上报 ActorChannelStatus
        try {
            signalingChannel = ManagedChannelBuilder
                    .forAddress(cp.getHost(), cp.getPort())
                    .usePlaintext()
                    .build();
            com.kekwy.iarnet.proto.agent.DeviceAgentRegistryServiceGrpc.DeviceAgentRegistryServiceStub stub =
                    com.kekwy.iarnet.proto.agent.DeviceAgentRegistryServiceGrpc.newStub(signalingChannel);

            StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage> sender =
                    stub.signalingChannel(new StreamObserver<>() {
                        @Override
                        public void onNext(com.kekwy.iarnet.proto.agent.SignalingMessage value) {
                            // 当前不处理来自控制平面的信令
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.warn("DeviceAgent SignalingChannel 出错: {}", t.getMessage());
                        }

                        @Override
                        public void onCompleted() {
                            log.info("DeviceAgent SignalingChannel 已关闭");
                        }
                    });

            LocalActorGraph.getInstance().setSignalingSender(sender);
            log.info("DeviceAgent SignalingChannel 已建立，准备上报本地 Actor 通道状态");
        } catch (Exception e) {
            log.warn("建立 DeviceAgent SignalingChannel 失败，将不会上报 ActorChannelStatus", e);
        }

        log.info("Adapter 已启动: type={}, name={}, control-plane={}:{}",
                props.getType(), props.getName(), cp.getHost(), cp.getPort());
    }

    @PreDestroy
    public void stop() {
        if (registryClient != null) {
            registryClient.close();
        }
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                log.warn("关闭 AdapterEngine 时出错", e);
            }
        }
        if (localAgentServer != null) {
            localAgentServer.shutdown();
            log.info("Local Device Agent 已停止");
        }
        if (signalingChannel != null) {
            signalingChannel.shutdown();
            log.info("DeviceAgent SignalingChannel 通道已关闭");
        }
        log.info("Adapter 已停止");
    }

    private static AdapterEngine createEngine(AdapterProperties props,
                                               com.kekwy.iarnet.proto.ir.Resource totalResource,
                                               ArtifactStore artifactStore) {
        return switch (props.getType().toLowerCase()) {
            case "docker" -> new DockerEngine(
                    props.getDocker().getHost(),
                    props.getDocker().getNetwork(),
                    props.getTags(),
                    totalResource,
                    artifactStore);
            case "k8s", "kubernetes" -> new KubernetesEngine(
                    props.getK8s().getKubeconfig(),
                    props.getK8s().isInCluster(),
                    props.getK8s().getNamespace(),
                    props.getTags(),
                    totalResource,
                    artifactStore);
            default -> throw new IllegalArgumentException(
                    "不支持的适配器类型: " + props.getType() + "，可选值: docker, k8s");
        };
    }
}
