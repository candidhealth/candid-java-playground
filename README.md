# candid-java-playground

Exploring Java as a potential new language for Candid's gRPC-based denial prediction service.

## Project Structure

```
candid-java-playground/
├── candid-api-proto/           # Protocol Buffer definitions
├── candid-api-grpc-server/     # gRPC server implementation
├── candid-api-grpc-client/     # gRPC client implementation
├── candid-api-python-client/   # Auto-generated Python client bindings
└── .github/workflows/          # CI/CD workflows
```

## Prerequisites

- **Java 21** - Required for building and running
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

### Gradle Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| com.google.protobuf | 0.9.5 | Protobuf code generation |
| com.palantir.git-version | 4.2.0 | Automatic versioning from git |
| com.palantir.consistent-versions | 3.7.0 | Dependency version management |
| com.google.cloud.artifactregistry | 2.2.5 | Artifact Registry publishing |

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
