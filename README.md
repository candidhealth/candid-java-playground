# candid-java-playground

Exploring Java as a potential new language for Candid's gRPC-based denial prediction service.

## Project Structure

```
candid-java-playground/
├── candid-api-proto/                  # Protocol Buffer definitions
├── candid-api-grpc-server/            # gRPC server implementation
├── candid-api-grpc-client/            # gRPC client implementation
├── candid-api-grpc-server-cloud-run/  # Cloud Run deployment (GraalVM native image)
├── candid-api-python-client/          # Auto-generated Python client bindings
└── .github/workflows/                 # CI/CD workflows
```

## Prerequisites

- **Java 21** or **GraalVM 21** - Required for building and running
- **Protocol Buffers Compiler (protoc)** - Required for proto file generation

### Installing protoc on macOS

```bash
# Using Homebrew
brew install protobuf

# Verify installation
protoc --version
# Should show: libprotoc 3.x.x or higher
```

For other platforms, see [Protocol Buffers Installation](https://grpc.io/docs/protoc-installation/).

### Installing GraalVM (Required for Native Images)

To build native images for Cloud Run deployment, you need GraalVM with the `native-image` tool installed.

**Option 1: SDKMAN (Recommended)**

```bash
# Install GraalVM 21 with SDKMAN
sdk install java 21.0.9-graal

# Verify installation
java -version
# Should show: GraalVM Runtime Environment Oracle GraalVM 21.0.9+7.1

native-image --version
# Should show: native-image 21.0.9
```

**Option 2: Homebrew**

```bash
# Using Homebrew
brew install --cask graalvm-jdk

# Verify installation
java -version
# Should show: Java 21.x.x with GraalVM

# Install native-image tool (may be needed depending on distribution)
gu install native-image
```

For other platforms, see [GraalVM Installation](https://www.graalvm.org/downloads/).

## Getting Started

### Build the Project

```bash
./gradlew build
```

This will:
1. Generate Java classes from `.proto` files
2. Compile all Java code
3. Run tests

### Run Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :candid-api-grpc-server:test
```

### Run the Server Locally

```bash
./gradlew :candid-api-grpc-server:run
```

The server will start on port 9090 by default.

## Dependency Management

This project uses [gradle-consistent-versions](https://github.com/palantir/gradle-consistent-versions) for centralized dependency management.

### How It Works

- **`versions.props`** - Define minimum required versions for dependencies
- **`versions.lock`** - Auto-generated lockfile with resolved versions for all transitive dependencies

### Adding a New Dependency

1. Add the dependency to a module's `build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation("com.google.guava:guava")
   }
   ```

2. Add the version constraint to `versions.props`:
   ```properties
   com.google.guava:guava = 32.1.3-jre
   ```

3. Regenerate the lockfile:
   ```bash
   ./gradlew --write-locks
   ```

### Updating Dependencies

**Manual updates:**
```bash
# Edit versions.props with new versions
vim versions.props

# Regenerate lockfile
./gradlew --write-locks
```

**Automated updates via Renovate:**

This project uses [Renovate](https://github.com/apps/renovate) for automated dependency updates. Renovate will:
- Scan for dependency updates weekly (Mondays at 3am PT)
- Group related packages (gRPC, Protobuf, JUnit) into single PRs
- Automatically update `versions.props` and regenerate `versions.lock`
- Run CI tests on each PR

See `.github/renovate.json` for configuration.

## Protocol Buffers (Protobuf)

### Proto File Location

Proto definitions are in `candid-api-proto/src/main/proto/`.

### Regenerating Java Code from Protos

Java code is automatically generated during the build:

```bash
./gradlew :candid-api-proto:generateProto
```

Generated files are located in:
```
candid-api-proto/build/generated/source/proto/main/
├── java/     # Message classes
└── grpc/     # Service stubs and implementations
```

### Modifying Proto Files

1. Edit the `.proto` file in `candid-api-proto/src/main/proto/`
2. Regenerate code:
   ```bash
   ./gradlew :candid-api-proto:generateProto
   ```
3. Update server/client implementations as needed
4. Run tests to verify changes:
   ```bash
   ./gradlew test
   ```

### Proto Configuration

The protobuf plugin is configured in `candid-api-proto/build.gradle.kts`:
- **Protobuf version**: 4.33.1
- **gRPC version**: 1.77.0
- **@Generated annotations**: Omitted (configured via `option("@generated=omit")`)

## Publishing

### Publishing the Server JAR

The server JAR is published to Google Artifact Registry Maven repository.

**Manually trigger publish:**

```bash
# From CI (GitHub Actions)
# Go to Actions → Publish Server JAR → Run workflow

# Locally (requires gcloud authentication)
gcloud auth application-default login
./gradlew :candid-api-grpc-server:publish -PenablePublishing=true
```

**Automatic publishing:**
- Pushes to `main` automatically publish to Artifact Registry
- Version is determined by `gradle-git-version` plugin (uses git tags)

**Published artifact:**
- **Group**: `com.candid`
- **Artifact**: `denial-predictor-server`
- **Repository**: `us-central1-maven.pkg.dev/candid-central/candid-maven-private`

### Publishing the Python Client

The Python client is auto-generated from proto files and published to Candid's private Python repository.

**Manually trigger publish:**

```bash
# From CI (GitHub Actions)
# Go to Actions → Publish Python Client → Run workflow
```

**What gets published:**
- Auto-generated Python bindings from `.proto` files
- Package name: `candid-denial-predictor-client`
- Repository: `us-central1-python.pkg.dev/candid-central/candid-python-private`

**Version syncing:**
- Python client version matches the Java version (via `gradle-git-version`)

### Versioning with gradle-git-version

Versions are automatically derived from git tags:

```bash
# Create a new version tag
git tag v1.0.0
git push origin v1.0.0

# Next build will use version 1.0.0
./gradlew properties | grep version
```

**Version format:**
- **Tagged commit**: Uses the tag (e.g., `v1.0.0` → version `1.0.0`)
- **Untagged commit**: Uses commit hash (e.g., `0.0.0-1-g1234abc`)
- **Dirty working tree**: Appends `.dirty` suffix

## Cloud Run Deployment

The `candid-api-grpc-server-cloud-run` module provides a production-ready deployment of the gRPC server to Google Cloud Run using GraalVM native image compilation.

### Why GraalVM Native Image?

**Performance Benefits:**
- **Startup time**: <100ms (vs 5-15s for traditional JVM)
- **Memory usage**: ~20-30MB (vs ~100MB for JVM)
- **Cost savings**: ~40% reduction with Cloud Run's scale-to-zero capability

**Trade-offs:**
- Longer build times (~2-5 minutes for native compilation)
- Some reflection-based libraries require configuration
- Best for bursty traffic patterns with frequent cold starts

### Building the Native Image with Gradle

**Requirements**: GraalVM 21 with `native-image` installed (see Prerequisites above)

```bash
# Build native image with Gradle (takes 1-2 minutes)
./gradlew :candid-api-grpc-server-cloud-run:nativeCompile

# Run the native image locally
./candid-api-grpc-server-cloud-run/build/native/nativeCompile/denial-predictor-server
```

**What happens during the build:**
- Gradle detects your installed GraalVM via toolchain or JAVA_HOME
- Compiles Java bytecode to a native binary (~105MB)
- Applies GraalVM configuration for Netty, gRPC, and reflection
- Output: Single executable with <100ms startup time

**Gradle configuration highlights:**
```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.GRAAL_VM)  // Requests GraalVM distribution
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("denial-predictor-server")
            mainClass.set("com.candid.api.cloudrun.CloudRunServer")
            buildArgs.add("--verbose")
        }
    }
    toolchainDetection.set(true)  // Auto-detect GraalVM from toolchain
}
```

### Building Container Images with Jib

For production CI/CD pipelines, use [Jib](https://github.com/GoogleContainerTools/jib) for Docker-less container building:

```bash
# Build native image and package with Jib (one command)
./gradlew :candid-api-grpc-server-cloud-run:jibNative

# Or build native image and push to Artifact Registry
./gradlew :candid-api-grpc-server-cloud-run:nativeCompile :candid-api-grpc-server-cloud-run:jib
```

### Alternative: Building with Docker

If you don't want to install GraalVM locally, you can use Docker with a multi-stage build:

```bash
# Build Docker image with GraalVM native image (takes 2-3 minutes)
docker build -f candid-api-grpc-server-cloud-run/Dockerfile \
  -t denial-predictor-server:local .

# Run the container locally
docker run -p 8080:8080 --name denial-predictor denial-predictor-server:local

# Stop and remove container
docker stop denial-predictor && docker rm denial-predictor
```

The Dockerfile uses a multi-stage build with GraalVM for compilation and debian:12-slim for runtime.

### Deploying to Cloud Run

```bash
# Deploy using gcloud CLI
gcloud run services replace candid-api-grpc-server-cloud-run/deploy/cloudrun.yaml \
  --region=us-central1

# Or use kubectl with Cloud Run
kubectl apply -f candid-api-grpc-server-cloud-run/deploy/cloudrun.yaml
```

**Cloud Run configuration** (`deploy/cloudrun.yaml`):
- **Autoscaling**: 0-10 instances (scale to zero when idle)
- **Concurrency**: 100 requests per instance
- **Memory**: 128Mi limit (64Mi request)
- **CPU**: 1 vCPU limit (0.5 vCPU request)
- **Health checks**: gRPC health checking protocol
- **Port**: 8080 (HTTP/2 Cleartext - Cloud Run handles TLS)

### Health Checks

The server implements the [gRPC Health Checking Protocol](https://github.com/grpc/grpc/blob/master/doc/health-checking.md) required by Cloud Run:

```bash
# Test health check locally with grpcurl
grpcurl -plaintext localhost:8080 grpc.health.v1.Health/Check
```

**Cloud Run health probe configuration:**
- **Liveness probe**: Checks every 3 seconds, restarts after 5 failures
- **Startup probe**: Allows up to 10 seconds for startup
- **Service**: `grpc.health.v1.Health`

### Testing Cloud Run Locally

Run the Cloud Run integration tests to simulate the Cloud Run environment:

```bash
# Run all Cloud Run tests
./gradlew :candid-api-grpc-server-cloud-run:test

# Run specific test
./gradlew :candid-api-grpc-server-cloud-run:test --tests CloudRunIntegrationTest
```

**What the tests verify:**
- PORT environment variable handling (Cloud Run requirement)
- gRPC health check protocol
- Service functionality
- Concurrent request handling
- Server restart/recovery

### GraalVM Configuration

Native image configuration is located in:
```
candid-api-grpc-server-cloud-run/src/main/resources/META-INF/native-image/
├── native-image.properties  # Build-time configuration
├── reflect-config.json      # Reflection configuration
└── resource-config.json     # Resource bundle configuration
```

**Key settings:**
- **Runtime initialization**: Netty and gRPC classes (required for compatibility)
- **Build-time initialization**: SLF4J logging
- **Reflection**: All protobuf message and service classes
- **Resources**: Logback configuration, application properties

### Environment Variables

The Cloud Run server respects these environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | Server port (set by Cloud Run) |
| `MALLOC_ARENA_MAX` | `2` | Reduce native memory usage |

### Troubleshooting Cloud Run Deployment

**Native image build fails:**
```bash
# Run with verbose output
./gradlew :candid-api-grpc-server-cloud-run:nativeCompile --info

# Common issues:
# - Missing reflection configuration for protobuf classes
# - Runtime initialization needed for Netty/gRPC
# - Resource bundles not included
```

**Container fails health checks:**
```bash
# Check logs
gcloud run services logs read denial-predictor --region=us-central1

# Test health endpoint locally
grpcurl -plaintext localhost:8080 grpc.health.v1.Health/Check
```

**High memory usage:**
```bash
# Check actual memory usage in Cloud Run metrics
gcloud run services describe denial-predictor --region=us-central1

# Native image should use ~20-30MB
# If higher, check for memory leaks or configuration issues
```

## CI/CD Workflows

### Test Workflow (`.github/workflows/test.yml`)

**Triggers:**
- Pull requests
- Pushes to `main`
- Merge queue

**What it does:**
- Runs `./gradlew build test`
- Uses Java 21
- Caches Gradle dependencies

### Publish Server Workflow (`.github/workflows/publish-server.yml`)

**Triggers:**
- Manual dispatch (workflow_dispatch)
- Pushes to `main`

**What it does:**
- Authenticates to Google Cloud via Workload Identity
- Publishes server JAR to Artifact Registry Maven repository
- Uses version from `gradle-git-version`

**Note:** Requires Workload Identity configuration (see placeholders in workflow file)

### Publish Python Client Workflow (`.github/workflows/publish-python-client.yml`)

**Triggers:**
- Manual dispatch (workflow_dispatch)
- Pushes to `main`

**What it does:**
- Generates Python bindings from proto files using `grpcio-tools`
- Builds Python package with Poetry
- Publishes to Candid's private Python repository
- Syncs version with Java using `gradle-git-version`

## Development Tips

### Gradle Daemon

The Gradle daemon is disabled by default (via `gradle.properties`) due to incompatibility with `gradle-git-version` and `artifactregistry` plugins when configuration cache is enabled.

To run with daemon (faster for local development):
```bash
./gradlew build --daemon
```

### Configuration Cache

Configuration cache is currently disabled to support `gradle-git-version` and `artifactregistry` plugins, which run external processes during configuration.

### IDE Setup

**IntelliJ IDEA:**
1. Import as Gradle project
2. Ensure Java 21 SDK is configured
3. Enable annotation processing if needed
4. Generated proto sources should be automatically recognized

### Viewing Generated Code

Generated protobuf code is in:
```bash
open candid-api-proto/build/generated/source/proto/main/
```

### Clean Build

```bash
# Clean all build artifacts
./gradlew clean

# Clean and rebuild
./gradlew clean build
```

## Project Dependencies

### Core Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| gRPC Java | 1.77.0 | gRPC implementation |
| Protobuf | 4.33.1 | Protocol Buffers runtime |
| SLF4J | 2.0.17 | Logging facade |
| Logback | 1.5.21 | Logging implementation |
| JUnit Jupiter | 6.0.1 | Testing framework |
| Testcontainers | 1.20.4 | Container integration testing |
| AssertJ | 3.27.3 | Fluent test assertions |
| GraalVM SDK | 24.2.0 | Native image compilation |

### Gradle Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| com.google.protobuf | 0.9.5 | Protobuf code generation |
| com.palantir.git-version | 4.2.0 | Automatic versioning from git |
| com.palantir.consistent-versions | 3.7.0 | Dependency version management |
| com.google.cloud.artifactregistry | 2.2.5 | Artifact Registry publishing |
| com.google.cloud.tools.jib | 3.5.1 | Docker-less container building |
| org.graalvm.buildtools.native | 0.10.4 | GraalVM native image builds |

## Troubleshooting

### Protobuf Generation Fails

**Error:** `protoc: command not found`

**Solution:**
```bash
brew install protobuf
```

### gcloud Errors During Build

**Error:** `Process 'command 'gcloud'' finished with non-zero exit value 1`

**Cause:** Artifact Registry plugin requires gcloud, but it's only needed for publishing.

**Solution:** Don't pass `-PenablePublishing=true` for regular builds:
```bash
# For builds/tests (no gcloud needed)
./gradlew build

# For publishing (gcloud required)
./gradlew publish -PenablePublishing=true
```

### Version Shows as "unspecified"

**Cause:** `gradle-git-version` couldn't determine version from git.

**Solution:** Ensure you're in a git repository and have commits:
```bash
git status  # Verify you're in a git repo
git log     # Verify you have commits
```

### Tests Failing

```bash
# Run tests with more verbose output
./gradlew test --info

# Run a specific test
./gradlew :candid-api-grpc-server:test --tests DenialPredictorE2ETest
```

## Contributing

1. Create a feature branch from `main`
2. Make your changes
3. Run tests: `./gradlew test`
4. Create a pull request
5. CI will run tests automatically
6. After approval, merge to `main`

## Resources

- [gRPC Java Documentation](https://grpc.io/docs/languages/java/)
- [Protocol Buffers Guide](https://protobuf.dev/programming-guides/proto3/)
- [Gradle Documentation](https://docs.gradle.org/current/userguide/userguide.html)
- [gradle-consistent-versions](https://github.com/palantir/gradle-consistent-versions)
- [gradle-git-version](https://github.com/palantir/gradle-git-version)
