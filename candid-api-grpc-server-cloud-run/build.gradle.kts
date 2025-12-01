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
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        named("main") {
            imageName.set("denial-predictor-server")
            mainClass.set("com.candid.api.cloudrun.CloudRunServer")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
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
jib {
    from {
        // Use distroless base image for minimal attack surface and size
        // For GraalVM native image, we only need a minimal base
        image = "gcr.io/distroless/base-debian12"
    }
    to {
        image = "us-central1-docker.pkg.dev/candid-central/candid-containers/denial-predictor-server"
        tags = setOf("latest", project.version.toString(), "native")
    }
    container {
        // For native image, we copy the pre-built binary
        // Jib will automatically detect and use the native-image output
        mainClass = "com.candid.api.cloudrun.CloudRunServer"
        ports = listOf("8080")
        environment = mapOf(
            // Reduce native memory arena count for lower memory usage
            "MALLOC_ARENA_MAX" to "2"
        )
        labels = mapOf(
            "org.opencontainers.image.source" to "https://github.com/candidhealth/candid-java-playground",
            "org.opencontainers.image.description" to "Denial Predictor gRPC Server (GraalVM Native Image)",
            "build.type" to "graalvm-native"
        )
        // Cloud Run best practices
        user = "nonroot:nonroot"
    }
}
