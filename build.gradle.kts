plugins {
    id("com.palantir.consistent-versions") version "3.7.0"
    id("com.palantir.git-version") version "4.2.0"
}

val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()

allprojects {
    group = "com.candid"
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}
