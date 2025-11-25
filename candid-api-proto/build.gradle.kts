plugins {
    id("java-library")
    id("com.google.protobuf")
}

dependencies {
    // Protobuf dependencies - use api() because generated code requires these at compile time
    api("com.google.protobuf:protobuf-java")
    api("io.grpc:grpc-protobuf")
    api("io.grpc:grpc-stub")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
