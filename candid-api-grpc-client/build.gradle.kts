plugins {
    id("java-library")
}

dependencies {
    // Depend on the proto module for generated code
    api(project(":candid-api-proto"))

    // gRPC runtime dependencies
    implementation("io.grpc:grpc-netty-shaded")

    // Logging
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
