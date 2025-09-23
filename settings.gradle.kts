pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
        flatDir {
            dirs("libs") // This tells Gradle to look in app/libs for .aar files
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        flatDir {
            dirs("libs") // This tells Gradle to look in app/libs for .aar files
        }
    }
}

rootProject.name = "VideoApp"
include(":app")
 