package com.simpledouyin.recommend;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class RecommendServiceApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceApplication.class);

    @Value("${recommend.grpc.port:9090}")
    private int grpcPort;

    private final RecommendServiceImpl recommendService;

    public RecommendServiceApplication(RecommendServiceImpl recommendService) {
        this.recommendService = recommendService;
    }

    public static void main(String[] args) {
        SpringApplication.run(RecommendServiceApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        Server server = ServerBuilder.forPort(grpcPort)
                .addService(recommendService)
                .build();
        try {
            server.start();
            log.info("gRPC server started on port {}", grpcPort);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start gRPC server", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            try {
                server.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for gRPC server shutdown", e);
            }
        }));
        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for gRPC server termination", e);
        }
    }
}
