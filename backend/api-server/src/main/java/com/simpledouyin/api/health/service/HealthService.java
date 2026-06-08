package com.simpledouyin.api.health.service;

import com.simpledouyin.api.health.dto.HealthResponse;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final String grpcHost;
    private final int grpcPort;

    public HealthService(
            JdbcTemplate jdbcTemplate,
            @Value("${recommend.grpc.host:localhost}") String grpcHost,
            @Value("${recommend.grpc.port:9090}") int grpcPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.grpcHost = grpcHost;
        this.grpcPort = grpcPort;
    }

    public HealthResponse check() {
        Map<String, String> components = new LinkedHashMap<>();

        // API Server 自身：能执行到这里说明服务在运行
        components.put("apiServer", UP);

        // MySQL 连接检查
        components.put("mysql", checkMysql());

        // gRPC Recommend Service 连通性检查
        components.put("recommendService", checkGrpc());

        String overallStatus = components.values().stream().allMatch(UP::equals) ? UP : DOWN;
        return new HealthResponse(overallStatus, components);
    }

    private String checkMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return UP;
        } catch (Exception e) {
            log.warn("MySQL health check failed", e);
            return DOWN;
        }
    }

    private String checkGrpc() {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();
            ConnectivityState state = channel.getState(true);
            // 等待最多 2 秒让 channel 尝试连接
            channel.notifyWhenStateChanged(state, () -> {});
            channel.awaitTermination(2, TimeUnit.SECONDS);
            // 忽略 awaitTermination 的返回值，直接检查最新状态
            return channel.getState(false) != ConnectivityState.SHUTDOWN
                    && channel.getState(false) != ConnectivityState.TRANSIENT_FAILURE
                    ? UP : DOWN;
        } catch (Exception e) {
            log.warn("gRPC health check failed", e);
            return DOWN;
        } finally {
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdownNow();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
