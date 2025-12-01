# Multi-stage Docker build for local testing of GraalVM native image
# This Dockerfile is useful for local development when you don't have GraalVM installed
#
# Production builds should use Jib (no Docker required) or CI/CD pipelines

# ============================================================================
# Stage 1: Build native image with GraalVM
# ============================================================================
FROM ghcr.io/graalvm/native-image-community:21 AS builder

# Install required build tools
RUN microdnf install -y findutils

WORKDIR /build

# Copy Gradle wrapper and build files
COPY gradle gradle
COPY gradlew gradlew.bat ./
COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY gradle.properties ./
COPY versions.props ./
COPY versions.lock ./
COPY buildSrc buildSrc

# Copy all module build files first (for better caching)
COPY candid-api-proto/build.gradle.kts candid-api-proto/
COPY candid-api-grpc-server/build.gradle.kts candid-api-grpc-server/
COPY candid-api-grpc-client/build.gradle.kts candid-api-grpc-client/
COPY candid-api-grpc-server-cloud-run/build.gradle.kts candid-api-grpc-server-cloud-run/

# Download dependencies (this layer will be cached if dependencies don't change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY candid-api-proto/src candid-api-proto/src
COPY candid-api-grpc-server/src candid-api-grpc-server/src
COPY candid-api-grpc-client/src candid-api-grpc-client/src
COPY candid-api-grpc-server-cloud-run/src candid-api-grpc-server-cloud-run/src

# Build native image
# Note: We skip tests here because they're already run in CI
# Use --no-daemon to avoid gradle daemon in Docker
# Set version to 'docker-local' since .git directory is not copied
RUN ./gradlew :candid-api-grpc-server-cloud-run:nativeCompile \
    --no-daemon \
    -x test \
    -Pversion=docker-local \
    && ls -lh /build/candid-api-grpc-server-cloud-run/build/native/nativeCompile/

# ============================================================================
# Stage 2: Runtime image with native binary
# ============================================================================
# Use debian-slim for runtime - includes all necessary shared libraries
# For production, you might want to use distroless/cc-debian12, but it requires
# matching the exact library versions between build and runtime
FROM debian:12-slim

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy native image from builder
COPY --from=builder \
    /build/candid-api-grpc-server-cloud-run/build/native/nativeCompile/denial-predictor-server \
    /app/denial-predictor-server

# Make binary executable and owned by appuser
RUN chmod +x /app/denial-predictor-server && \
    chown appuser:appuser /app/denial-predictor-server

# Cloud Run sends PORT environment variable
ENV PORT=8080

# Reduce native memory usage
ENV MALLOC_ARENA_MAX=2

# Expose port (documentation only, Cloud Run ignores EXPOSE)
EXPOSE 8080

# Run as non-root user
USER appuser:appuser

# Run the native image
ENTRYPOINT ["/app/denial-predictor-server"]
