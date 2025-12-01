plugins {
    id("java-library")
    id("maven-publish")
}

// Only apply artifactregistry plugin when explicitly enabled (requires gcloud)
// Enable with: ./gradlew publish -PenablePublishing=true
if (project.findProperty("enablePublishing") == "true") {
    apply(plugin = "com.google.cloud.artifactregistry.gradle-plugin")
}

dependencies {
    // Depend on the proto module for generated code
    api(project(":candid-api-proto"))

    // gRPC runtime dependencies
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-services")

    // Machine Learning
    implementation("ml.dmlc:xgboost4j_2.12:1.7.6")
    implementation("ml.dmlc:xgboost4j-spark_2.12:1.7.6")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

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
