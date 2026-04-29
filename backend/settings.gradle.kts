pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Java 17 toolchain 자동 다운로드 (Foojay Disco API 사용)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "iroum-cms-backend"
