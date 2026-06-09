package com.simpledouyin.api.health.service;

import com.simpledouyin.api.health.dto.HealthResponse;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final long GRPC_TIMEOUT_NANOS = 2_000_000_000L; // 2 秒
    private static final long GRPC_POLL_INTERVAL_MS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ManagedChannel grpcChannel;

    public HealthService(JdbcTemplate jdbcTemplate, ManagedChannel grpcChannel) {
        this.jdbcTemplate = jdbcTemplate;
        this.grpcChannel = grpcChannel;
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

    /**
     * 通过 gRPC Channel 状态检测 recommendService 连通性。
     * 使用轮询等待 READY 或 TRANSIENT_FAILURE，最长等待 2 秒。
     */
    private String checkGrpc() {
        try {
            ConnectivityState state = grpcChannel.getState(true);
            long deadline = System.nanoTime() + GRPC_TIMEOUT_NANOS;

            while (state != ConnectivityState.READY
                    && state != ConnectivityState.TRANSIENT_FAILURE
                    && System.nanoTime() < deadline) {
                Thread.sleep(GRPC_POLL_INTERVAL_MS);
                state = grpcChannel.getState(false);
            }

            if (state == ConnectivityState.READY) {
                return UP;
            }
            log.warn("gRPC health check failed: state={}", state);
            return DOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("gRPC health check interrupted", e);
            return DOWN;
        } catch (Exception e) {
            log.warn("gRPC health check failed", e);
            return DOWN;
        }
    }
}
