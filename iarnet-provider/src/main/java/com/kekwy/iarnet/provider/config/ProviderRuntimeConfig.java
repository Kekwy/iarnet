package com.kekwy.iarnet.provider.config;

import com.kekwy.iarnet.provider.actor.ActorRegistrationServiceGrpcImpl;
import com.kekwy.iarnet.provider.artifact.ArtifactFetcher;
import com.kekwy.iarnet.provider.artifact.ArtifactStore;
import com.kekwy.iarnet.provider.control.ControlService;
import com.kekwy.iarnet.provider.deployment.DeploymentService;
import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.provider.engine.docker.DockerEngine;
import com.kekwy.iarnet.provider.engine.k8s.KubernetesEngine;
import com.kekwy.iarnet.provider.registry.ProviderRegistryClient;
import com.kekwy.iarnet.provider.signaling.SignalingService;
import com.kekwy.iarnet.proto.fabric.ProviderRegistryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provider 运行时 Bean 配置：ArtifactStore、ProviderEngine、ArtifactFetcher、
 * Actor 注册 gRPC Server、ProviderRegistryClient。
 * <p>
 * 创建与销毁顺序由依赖与 @DependsOn 保证：先启动 actorRegistrationServer，
 * 再创建并 start ProviderRegistryClient。
 */
@Configuration
public class ProviderRuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderRuntimeConfig.class);

    @Bean
    public ArtifactStore artifactStore(ProviderProperties props) {
        return new ArtifactStore(Path.of(props.getArtifactDir()));
    }

    @Bean(destroyMethod = "close")
    public ProviderEngine providerEngine(ProviderProperties props, ArtifactStore artifactStore) {
        return createEngine(props, artifactStore);
    }

    @Bean
    public ArtifactFetcher artifactFetcher(ArtifactStore artifactStore) {
        return new ArtifactFetcher(artifactStore);
    }

    @Bean(name = "actorRegistrationServer", destroyMethod = "shutdown")
    public Server actorRegistrationServer(ProviderProperties props,
                                          ActorRegistrationServiceGrpcImpl actorRegistrationService) {
        int port = props.getActorRegistry().getPort();
        Server server = ServerBuilder.forPort(port)
                .addService(actorRegistrationService)
                .build();
        try {
            server.start();
            log.info("ActorRegistrationService 已启动: port={}", port);
        } catch (Exception e) {
            throw new IllegalStateException("启动 ActorRegistrationService 失败: port=" + port, e);
        }
        return server;
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel controlPlaneChannel(ProviderProperties props) {
        var cp = props.getRegistry();
        return ManagedChannelBuilder.forAddress(cp.getHost(), cp.getPort()).usePlaintext().build();
    }

    @Bean
    public ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub providerRegistryBlockingStub(
            ManagedChannel controlPlaneChannel) {
        return ProviderRegistryServiceGrpc.newBlockingStub(controlPlaneChannel);
    }

    @Bean
    public ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub(
            ManagedChannel controlPlaneChannel) {
        return ProviderRegistryServiceGrpc.newStub(controlPlaneChannel);
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService providerScheduler() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean(destroyMethod = "close")
    @DependsOn("actorRegistrationServer")
    public ProviderRegistryClient providerRegistryClient(ProviderProperties props,
                                                         ProviderIdentity providerIdentity,
                                                         ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub providerRegistryBlockingStub,
                                                         ControlService controlService,
                                                         DeploymentService deploymentService,
                                                         SignalingService signalingService) {
        var cp = props.getRegistry();
        ProviderRegistryClient client = new ProviderRegistryClient(
                providerRegistryBlockingStub,
                props,
                providerIdentity,
                controlService,
                deploymentService,
                signalingService);
        client.start();
        log.info("Provider 已连接控制平面: type={}, name={}, control-plane={}:{}",
                props.getType(), props.getName(), cp.getHost(), cp.getPort());
        return client;
    }

    private static ProviderEngine createEngine(ProviderProperties props, ArtifactStore artifactStore) {
        String host = props.getHost() != null && !props.getHost().isBlank()
                ? props.getHost() : "host.docker.internal";
        String actorRegistryAddr = host + ":" + props.getActorRegistry().getPort();
        var cp = props.getRegistry();

        return switch (props.getType().toLowerCase()) {
            case "docker" -> new DockerEngine(
                    props.getDocker().getHost(),
                    props.getDocker().getNetwork(),
                    artifactStore,
                    actorRegistryAddr);
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
