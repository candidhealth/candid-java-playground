plugins {
    id("com.palantir.consistent-versions") version "3.7.0"
    id("com.palantir.git-version") version "4.2.0"
}

// Use git version if available, otherwise fall back to "unspecified" or provided version
// This allows building in Docker without copying .git directory
version = if (project.hasProperty("version") && project.property("version") != "unspecified") {
    project.property("version") as String
} else {
    try {
        val gitVersion: groovy.lang.Closure<String> by extra
        gitVersion()
    } catch (e: Exception) {
        "docker-local"
    }
}

allprojects {
    group = "com.candid"
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}
