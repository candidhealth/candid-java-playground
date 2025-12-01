package com.candid.api.cloudrun;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the gRPC Health Checking Protocol (grpc.health.v1.Health).
 *
 * <p>This service is required for Cloud Run liveness probes. Cloud Run will periodically
 * call this service to determine if the container is healthy. If health checks fail
 * repeatedly, Cloud Run will restart the container.
 *
 * <p>Health check configuration in Cloud Run:
 * <pre>
 * livenessProbe:
 *   grpc:
 *     service: grpc.health.v1.Health
 *   initialDelaySeconds: 10
 *   periodSeconds: 3
 *   timeoutSeconds: 3
 *   failureThreshold: 5
 * </pre>
 *
 * <p>For more details, see:
 * <a href="https://github.com/grpc/grpc/blob/master/doc/health-checking.md">
 * gRPC Health Checking Protocol</a>
 */
public class HealthCheckService extends HealthGrpc.HealthImplBase {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    private volatile ServingStatus currentStatus = ServingStatus.SERVING;

    /**
     * Handles health check requests.
     *
     * <p>This implementation currently returns a simple SERVING status. In a production
     * environment, you would check:
     * <ul>
     *   <li>Database connections</li>
     *   <li>Dependency service availability</li>
     *   <li>Internal state consistency</li>
     *   <li>Resource availability (memory, file handles, etc.)</li>
     * </ul>
     *
     * @param request the health check request (may specify a service name to check)
     * @param responseObserver the response stream
     */
    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceName = request.getService();

        // Empty service name means overall server health
        if (serviceName.isEmpty()) {
            logger.debug("Health check requested for overall server health");
        } else {
            logger.debug("Health check requested for service: {}", serviceName);
        }

        // TODO: Add actual health checks here
        // Examples:
        // - Check database connection pool health
        // - Verify critical dependencies are reachable
        // - Check if service is accepting requests
        // - Verify internal state is consistent

        HealthCheckResponse response = HealthCheckResponse.newBuilder()
                .setStatus(currentStatus)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Handles watch requests for health status changes.
     *
     * <p>This allows clients to subscribe to health status updates rather than polling.
     * Cloud Run typically doesn't use this, preferring the simple check() method.
     *
     * @param request the health check request
     * @param responseObserver the response stream for status updates
     */
    @Override
    public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceName = request.getService();
        logger.debug("Health watch requested for service: {}", serviceName);

        // Send initial status
        HealthCheckResponse response = HealthCheckResponse.newBuilder()
                .setStatus(currentStatus)
                .build();

        responseObserver.onNext(response);

        // Note: In a real implementation, you would:
        // 1. Register this observer to receive status updates
        // 2. Send new responses when status changes
        // 3. Handle client disconnection
        //
        // For Cloud Run, the simple check() method is sufficient.
    }

    /**
     * Updates the health status.
     *
     * <p>Call this method to mark the service as unhealthy (e.g., during graceful
     * shutdown or when critical dependencies fail).
     *
     * @param status the new serving status
     */
    public void setStatus(ServingStatus status) {
        if (this.currentStatus != status) {
            logger.info("Health status changed: {} -> {}", this.currentStatus, status);
            this.currentStatus = status;
        }
    }

    /**
     * Marks the service as not serving.
     *
     * <p>Useful during graceful shutdown to stop accepting new requests
     * while allowing in-flight requests to complete.
     */
    public void markNotServing() {
        setStatus(ServingStatus.NOT_SERVING);
    }

    /**
     * Marks the service as serving and ready to accept requests.
     */
    public void markServing() {
        setStatus(ServingStatus.SERVING);
    }
}
