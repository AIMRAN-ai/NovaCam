pluginManagement {
    repositories {
        maven("https://dl.google.com/dl/android/maven2")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://dl.google.com/dl/android/maven2")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
    }
}

rootProject.name = "PixelForge"
include(":app")
