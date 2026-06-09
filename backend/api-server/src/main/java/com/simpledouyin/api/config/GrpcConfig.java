package com.simpledouyin.api.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * gRPC 客户端配置，将 ManagedChannel 作为单例 Bean 管理生命周期，
 * 避免每次健康检查重复创建/销毁 Channel（含 Netty event loop 和线程池）。
 */
@Configuration
public class GrpcConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcConfig.class);

    private ManagedChannel channel;

    @Bean
    public ManagedChannel grpcChannel(
            @Value("${recommend.grpc.host:localhost}") String host,
            @Value("${recommend.grpc.port:9090}") int port
    ) {
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        log.info("gRPC ManagedChannel created for {}:{}", host, port);
        return channel;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
            log.info("gRPC ManagedChannel shut down");
        }
    }
}
