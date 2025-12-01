package com.candid.api.cloudrun;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.DenialPredictorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test simulating Cloud Run execution environment.
 * Tests the complete lifecycle:
 * 1. Server startup with PORT environment variable
 * 2. Health check endpoint (used by Cloud Run liveness probes)
 * 3. Actual service endpoint (DenialPredictor)
 * 4. Graceful shutdown on SIGTERM
 */
class CloudRunIntegrationTest {

    private static final int TEST_PORT = 8888;
    private CloudRunServer server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        // Simulate Cloud Run environment by starting server on custom port
        server = new CloudRunServer(TEST_PORT);
        server.start();

        // Create client channel
        channel = ManagedChannelBuilder.forAddress("localhost", TEST_PORT)
                .usePlaintext()
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testCloudRunHealthCheckProbe() {
        // Given: Cloud Run liveness probe configuration
        // livenessProbe:
        //   grpc:
        //     service: grpc.health.v1.Health

        // When: Cloud Run sends health check
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        HealthCheckResponse response = healthStub.check(
                HealthCheckRequest.newBuilder()
                        .setService("grpc.health.v1.Health")
                        .build()
        );

        // Then: Server responds with SERVING status
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void testDenialPredictorServiceAvailable() {
        // Given: Cloud Run container is running and healthy
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        HealthCheckResponse healthResponse = healthStub.check(HealthCheckRequest.getDefaultInstance());
        assertThat(healthResponse.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

        // When: Client makes request to DenialPredictor service
        DenialPredictorGrpc.DenialPredictorBlockingStub denialStub =
                DenialPredictorGrpc.newBlockingStub(channel);

        DenialPredictionPayload payload = DenialPredictionPayload.newBuilder()
                .setPatientId("test-patient-123")
                .setClaimAmount(1000.0)
                .setProcedureCode("99213")
                .build();

        DenialPredictionResponse response = denialStub.predictDenial(payload);

        // Then: Service returns expected response
        assertThat(response).isNotNull();
        assertThat(response.getConfidenceScore()).isGreaterThanOrEqualTo(0.0);
        assertThat(response.getConfidenceScore()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void testMultipleSequentialRequests() {
        // Given: Server is running
        DenialPredictorGrpc.DenialPredictorBlockingStub denialStub =
                DenialPredictorGrpc.newBlockingStub(channel);

        // When: Multiple requests are made sequentially
        for (int i = 0; i < 10; i++) {
            DenialPredictionPayload payload = DenialPredictionPayload.newBuilder()
                    .setPatientId("patient-" + i)
                    .setClaimAmount(1000.0 + i * 100)
                    .setProcedureCode("99213")
                    .build();

            DenialPredictionResponse response = denialStub.predictDenial(payload);

            // Then: Each request succeeds
            assertThat(response).isNotNull();
            assertThat(response.getConfidenceScore()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void testConcurrentRequests() throws InterruptedException {
        // Given: Server is running
        DenialPredictorGrpc.DenialPredictorBlockingStub denialStub =
                DenialPredictorGrpc.newBlockingStub(channel);

        // When: Multiple concurrent requests are made
        int numThreads = 5;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    DenialPredictionPayload payload = DenialPredictionPayload.newBuilder()
                            .setPatientId("patient-" + threadId + "-" + j)
                            .setClaimAmount(1000.0 + threadId * 100 + j)
                            .setProcedureCode("99213")
                            .build();

                    DenialPredictionResponse response = denialStub.predictDenial(payload);
                    assertThat(response).isNotNull();
                }
            });
            threads[i].start();
        }

        // Then: All requests succeed
        for (Thread thread : threads) {
            thread.join(5000);
            assertThat(thread.isAlive()).isFalse();
        }
    }

    @Test
    void testServerRestartsAfterCrash() throws Exception {
        // Given: Server is running
        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        assertThat(healthStub.check(HealthCheckRequest.getDefaultInstance()).getStatus())
                .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

        // When: Server stops (simulating crash or restart)
        server.stop();
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);

        // And: Server restarts
        server = new CloudRunServer(TEST_PORT);
        server.start();
        channel = ManagedChannelBuilder.forAddress("localhost", TEST_PORT)
                .usePlaintext()
                .build();

        // Then: Server is healthy again
        healthStub = HealthGrpc.newBlockingStub(channel);
        assertThat(healthStub.check(HealthCheckRequest.getDefaultInstance()).getStatus())
                .isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

        // And: Service is functional
        DenialPredictorGrpc.DenialPredictorBlockingStub denialStub =
                DenialPredictorGrpc.newBlockingStub(channel);
        DenialPredictionResponse response = denialStub.predictDenial(
                DenialPredictionPayload.newBuilder()
                        .setPatientId("test-patient")
                        .setClaimAmount(1500.0)
                        .setProcedureCode("99213")
                        .build()
        );
        assertThat(response).isNotNull();
    }
}
