pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2") // Google mirror of Maven Central
        maven("https://maven.aliyun.com/repository/public")                    // Alibaba mirror (backup)
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2") // Google mirror of Maven Central
        maven("https://maven.aliyun.com/repository/public")                    // Alibaba mirror (backup)
        mavenCentral()
    }
}

rootProject.name = "Cura"
include(":app")
