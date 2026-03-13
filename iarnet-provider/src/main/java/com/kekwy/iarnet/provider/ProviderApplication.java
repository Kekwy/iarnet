package com.kekwy.iarnet.provider;

import com.kekwy.iarnet.provider.actor.ActorRegistrationServiceImpl;
import com.kekwy.iarnet.provider.actor.LocalActorGraph;
import com.kekwy.iarnet.provider.artifact.ArtifactFetcher;
import com.kekwy.iarnet.provider.artifact.ArtifactStore;
import com.kekwy.iarnet.provider.config.ProviderProperties;
import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.provider.engine.docker.DockerEngine;
import com.kekwy.iarnet.provider.engine.k8s.KubernetesEngine;
import com.kekwy.iarnet.provider.registry.ProviderRegistryClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;

/**
 * Provider 启动入口：注册到控制平面，建立 Control / Deployment / Signaling 三通道，
 * 并启动本地 ActorRegistrationService 供 Actor 连接。
 */
@SpringBootApplication
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderApplication {

    private static final Logger log = LoggerFactory.getLogger(ProviderApplication.class);

    private final ProviderProperties props;

    private ProviderEngine engine;
    private ProviderRegistryClient registryClient;
    private Server actorRegistrationServer;

    public ProviderApplication(ProviderProperties props) {
        this.props = props;
    }

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ArtifactStore artifactStore = new ArtifactStore(Path.of(props.getArtifactDir()));
        engine = createEngine(props, artifactStore);
        ArtifactFetcher artifactFetcher = new ArtifactFetcher(artifactStore);

        int agentPort = props.getActorAgent().getPort();
        ActorRegistrationServiceImpl actorRegistrationService = new ActorRegistrationServiceImpl();
        try {
            actorRegistrationServer = ServerBuilder.forPort(agentPort)
                    .addService(actorRegistrationService)
                    .build()
                    .start();
            LocalActorGraph.getInstance().setActorRegistrationService(actorRegistrationService);
            log.info("ActorRegistrationService 已启动: port={}", agentPort);
        } catch (Exception e) {
            throw new IllegalStateException("启动 ActorRegistrationService 失败: port=" + agentPort, e);
        }

        var cp = props.getControlPlane();
        registryClient = new ProviderRegistryClient(
                props.getName(),
                props.getDescription(),
                props.getZone(),
                engine.providerType(),
                props.getTags(),
                engine,
                artifactFetcher,
                cp.getHost(),
                cp.getPort());
        registryClient.start();

        log.info("Provider 已启动: type={}, name={}, control-plane={}:{}",
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
                log.warn("关闭 ProviderEngine 时出错", e);
            }
        }
        if (actorRegistrationServer != null) {
            actorRegistrationServer.shutdown();
            log.info("ActorRegistrationService 已停止");
        }
        log.info("Provider 已停止");
    }

    private static ProviderEngine createEngine(ProviderProperties props, ArtifactStore artifactStore) {
        String host = props.getHost() != null && !props.getHost().isBlank()
                ? props.getHost() : "host.docker.internal";
        String actorAgentAddr = host + ":" + props.getActorAgent().getPort();
        var cp = props.getControlPlane();
        String cpHost = ("127.0.0.1".equals(cp.getHost()) || "localhost".equalsIgnoreCase(cp.getHost()))
                ? host : cp.getHost();
        String controlPlaneAddr = cpHost + ":" + cp.getPort();

        return switch (props.getType().toLowerCase()) {
            case "docker" -> new DockerEngine(
                    props.getDocker().getHost(),
                    props.getDocker().getNetwork(),
                    artifactStore,
                    actorAgentAddr,
                    controlPlaneAddr);
            case "k8s", "kubernetes" -> new KubernetesEngine(
                    props.getK8s().getKubeconfig(),
                    props.getK8s().isInCluster(),
                    props.getK8s().getNamespace(),
                    artifactStore);
            default -> throw new IllegalArgumentException(
                    "不支持的引擎类型: " + props.getType() + "，可选: docker, k8s");
        };
    }
}
