package com.candid.api.cloudrun;

import com.candid.api.server.DenialPredictorService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Cloud Run-compatible gRPC server for the Denial Predictor service.
 *
 * <p>This server wraps the core DenialPredictorService and adds Cloud Run-specific
 * functionality:
 * <ul>
 *   <li>Reads the PORT environment variable (required by Cloud Run)</li>
 *   <li>Implements gRPC health checking protocol for liveness probes</li>
 *   <li>Handles graceful shutdown on SIGTERM</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * # Locally
 * java -jar server.jar
 *
 * # With custom port
 * PORT=9090 java -jar server.jar
 * </pre>
 */
public class CloudRunServer {
    private static final Logger logger = LoggerFactory.getLogger(CloudRunServer.class);
    private static final int DEFAULT_PORT = 8080;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final Server server;
    private final int port;

    public CloudRunServer(int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
                // Add the main denial predictor service
                .addService(new DenialPredictorService())
                // Add health check service (required for Cloud Run liveness probes)
                .addService(new HealthCheckService())
                .build();
    }

    /**
     * Starts the gRPC server and registers shutdown hook.
     */
    public void start() throws IOException {
        server.start();
        logger.info("✅ gRPC server started on port {} (Cloud Run ready)", port);
        logger.info("   Health check endpoint: grpc.health.v1.Health");
        logger.info("   Service endpoint: candid.api.DenialPredictor");

        // Register shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("⚠️  Received shutdown signal, initiating graceful shutdown...");
            try {
                CloudRunServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("❌ Error during shutdown", e);
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Stops the server gracefully, allowing in-flight requests to complete.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            logger.info("Stopping gRPC server...");
            server.shutdown();

            // Wait for server to terminate gracefully
            if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Server did not terminate gracefully within {}s, forcing shutdown",
                        SHUTDOWN_TIMEOUT_SECONDS);
                server.shutdownNow();

                // Wait for forced shutdown
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Server did not terminate after forced shutdown");
                }
            } else {
                logger.info("✅ Server stopped gracefully");
            }
        }
    }

    /**
     * Blocks until the server is terminated.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Reads the PORT environment variable (Cloud Run requirement).
     *
     * @return the port number from the PORT env var, or DEFAULT_PORT if not set
     */
    private static int getPort() {
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                return Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                logger.warn("Invalid PORT environment variable '{}', using default {}",
                        portEnv, DEFAULT_PORT);
                return DEFAULT_PORT;
            }
        }
        return DEFAULT_PORT;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Cloud Run injects the PORT environment variable
        int port = getPort();
        logger.info("\uD83D\uDE80 Starting Denial Predictor gRPC Server for Cloud Run");
        logger.info("   Port: {} (from {})", port,
                System.getenv("PORT") != null ? "PORT env var" : "default");

        CloudRunServer server = new CloudRunServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}
