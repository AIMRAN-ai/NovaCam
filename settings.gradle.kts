pluginManagement {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
    }
}

rootProject.name = "PixelForge"
include(":app")
