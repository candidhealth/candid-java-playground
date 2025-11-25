plugins {
    id("java-library")
    id("maven-publish")
    id("com.google.cloud.artifactregistry.gradle-plugin")
}

dependencies {
    // Depend on the proto module for generated code
    api(project(":candid-api-proto"))

    // gRPC runtime dependencies
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-services")

    // Logging
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")

    // Test dependencies
    testImplementation(project(":candid-api-grpc-client"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.candid"
            artifactId = "denial-predictor-server"
        }
    }
    repositories {
        maven {
            url = uri("artifactregistry://us-central1-maven.pkg.dev/candid-central/candid-maven-private")
        }
    }
}
