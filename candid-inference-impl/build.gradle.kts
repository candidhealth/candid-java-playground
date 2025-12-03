plugins {
    id("java-library")
}

dependencies {
    // ONNX Runtime for model inference
    implementation("com.microsoft.onnxruntime:onnxruntime")

    // JSON parsing
    implementation("com.google.code.gson:gson")

    // Logging
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
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
