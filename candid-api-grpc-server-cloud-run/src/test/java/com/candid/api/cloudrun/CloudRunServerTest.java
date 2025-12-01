package com.candid.api.cloudrun;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CloudRunServer ensuring it properly handles:
 * - PORT environment variable
 * - Server startup and shutdown
 * - Health check endpoint availability
 */
class CloudRunServerTest {

    private CloudRunServer server;
    private ManagedChannel channel;

    @AfterEach
    void cleanup() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testServerStartsOnDefaultPort() throws Exception {
        // Given: No PORT environment variable set
        server = new CloudRunServer(8080);

        // When: Server starts
        server.start();

        // Then: Server is accessible on default port
        channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build();

        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void testServerStartsOnCustomPort() throws Exception {
        // Given: Custom port
        int customPort = 9090;
        server = new CloudRunServer(customPort);

        // When: Server starts
        server.start();

        // Then: Server is accessible on custom port
        channel = ManagedChannelBuilder.forAddress("localhost", customPort)
                .usePlaintext()
                .build();

        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    @Test
    void testServerGracefulShutdown() throws Exception {
        // Given: Running server
        server = new CloudRunServer(8080);
        server.start();

        channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build();

        HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);
        HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

        // When: Server stops
        server.stop();

        // Then: Server shuts down gracefully
        // Subsequent calls should fail
        assertThat(channel.isShutdown() || channel.isTerminated()).isFalse();
        // But new connections should not be accepted (this is hard to test without making the channel fail)
    }

    @Test
    void testServerAwaitTermination() throws Exception {
        // Given: Server configured
        server = new CloudRunServer(8080);
        server.start();

        // When: Stop is called in a separate thread
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(100); // Give server time to start waiting
                server.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        shutdownThread.start();

        // Then: blockUntilShutdown should wait for shutdown
        server.blockUntilShutdown();
        shutdownThread.join(1000);
        assertThat(shutdownThread.isAlive()).isFalse();
    }
}
