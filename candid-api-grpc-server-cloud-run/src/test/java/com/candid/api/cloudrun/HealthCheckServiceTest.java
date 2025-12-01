package com.candid.api.cloudrun;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HealthCheckService ensuring it implements the gRPC Health Checking Protocol correctly.
 * This is critical for Cloud Run liveness probes.
 */
class HealthCheckServiceTest {

    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService();
    }

    @Test
    void testHealthCheckReturnsServing() {
        // Given: A health check request
        HealthCheckRequest request = HealthCheckRequest.getDefaultInstance();
        AtomicReference<HealthCheckResponse> responseRef = new AtomicReference<>();
        AtomicReference<Boolean> completedRef = new AtomicReference<>(false);

        StreamObserver<HealthCheckResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(HealthCheckResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException("Health check should not error", t);
            }

            @Override
            public void onCompleted() {
                completedRef.set(true);
            }
        };

        // When: Health check is called
        healthCheckService.check(request, responseObserver);

        // Then: Response indicates SERVING status
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        assertThat(completedRef.get()).isTrue();
    }

    @Test
    void testHealthCheckWithServiceName() {
        // Given: A health check request with a specific service name
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
                .setService("grpc.health.v1.Health")
                .build();
        AtomicReference<HealthCheckResponse> responseRef = new AtomicReference<>();
        AtomicReference<Boolean> completedRef = new AtomicReference<>(false);

        StreamObserver<HealthCheckResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(HealthCheckResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException("Health check should not error", t);
            }

            @Override
            public void onCompleted() {
                completedRef.set(true);
            }
        };

        // When: Health check is called
        healthCheckService.check(request, responseObserver);

        // Then: Response indicates SERVING status
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        assertThat(completedRef.get()).isTrue();
    }

    @Test
    void testWatchHealthCheckReturnsServing() {
        // Given: A health check watch request
        HealthCheckRequest request = HealthCheckRequest.getDefaultInstance();
        AtomicReference<HealthCheckResponse> responseRef = new AtomicReference<>();

        StreamObserver<HealthCheckResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(HealthCheckResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                // Watch endpoint sends initial status and keeps connection open
            }

            @Override
            public void onCompleted() {
                // Watch endpoint typically doesn't complete
            }
        };

        // When: Health check watch is called
        healthCheckService.watch(request, responseObserver);

        // Then: Initial response indicates SERVING status
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }
}
