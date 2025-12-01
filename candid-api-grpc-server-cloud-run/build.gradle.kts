plugins {
    id("java")
    id("com.google.cloud.tools.jib")
    id("org.graalvm.buildtools.native")
}

dependencies {
    // Depend on the existing gRPC server implementation
    implementation(project(":candid-api-grpc-server"))

    // gRPC health check support (required for Cloud Run liveness probes)
    implementation("io.grpc:grpc-services")

    // Use non-shaded Netty for GraalVM compatibility
    implementation("io.grpc:grpc-netty")

    // GraalVM SDK for native-image hints
    implementation("org.graalvm.sdk:nativeimage")

    // Logging
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        // Request GraalVM distribution
        // If you have GraalVM installed locally (via SDKMAN, Homebrew, etc.),
        // Gradle will auto-detect it. This ensures the native-image tool is available.
        vendor.set(JvmVendorSpec.GRAAL_VM)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

// GraalVM Native Image configuration
// Requires GraalVM with native-image tool installed (see README for installation)
// The toolchain configuration above tells Gradle to use your installed GraalVM
graalvmNative {
    binaries {
        named("main") {
            imageName.set("denial-predictor-server")
            mainClass.set("com.candid.api.cloudrun.CloudRunServer")

            // Verbose output for debugging build issues
            buildArgs.add("--verbose")

            // Don't fall back to JVM if native image fails
            buildArgs.add("--no-fallback")

            // Show exception stack traces during build
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }

    // Use Java toolchains to find GraalVM
    // Gradle will detect GraalVM installed via SDKMAN, Homebrew, or JAVA_HOME
    toolchainDetection.set(true)
}

// Custom task to build native image for local testing
tasks.register("buildNativeImage") {
    dependsOn("nativeCompile")
    doLast {
        println("✅ Native image built: build/native/nativeCompile/denial-predictor-server")
        println("   Run with: ./build/native/nativeCompile/denial-predictor-server")
    }
}

// Jib configuration for building container images
// For native images: Run `./gradlew nativeCompile jibDockerBuild` to build native binary first
// For JVM images: Run `./gradlew jibDockerBuild` directly
jib {
    from {
        // debian-slim provides shared libraries needed by GraalVM native images
        // For JVM builds, you might want to use eclipse-temurin:21-jre-alpine instead
        image = "debian:12-slim"
    }
    to {
        image = "us-central1-docker.pkg.dev/candid-central/candid-containers/denial-predictor-server"
        tags = setOf("latest", project.version.toString(), "native")
    }
    container {
        mainClass = "com.candid.api.cloudrun.CloudRunServer"
        ports = listOf("8080")

        // Create and use non-root user
        user = "1000:1000"

        environment = mapOf(
            "PORT" to "8080",
            "MALLOC_ARENA_MAX" to "2"
        )
        labels = mapOf(
            "org.opencontainers.image.source" to "https://github.com/candidhealth/candid-java-playground",
            "org.opencontainers.image.description" to "Denial Predictor gRPC Server",
            "org.opencontainers.image.version" to project.version.toString()
        )
    }

    // Custom configuration for native vs JVM builds
    extraDirectories {
        paths {
            // If native image exists, copy it into the container
            path {
                setFrom(file("build/native/nativeCompile"))
                into = "/app"
            }
        }
    }
}

// Task to build native image and package in container
tasks.register("jibNative") {
    group = "build"
    description = "Build GraalVM native image and package in Docker container"
    dependsOn("nativeCompile", "jibDockerBuild")

    doFirst {
        println("Building native image and packaging with Jib...")
    }
}
