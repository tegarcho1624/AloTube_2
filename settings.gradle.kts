pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor y nanojson se publican en JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AloTube2"
include(":app")
