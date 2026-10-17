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
    }
}

rootProject.name = "KarooPantallas"
// El SDK karoo-ext de Hammerhead va incluido como código fuente (licencia Apache 2.0),
// así no hace falta un token de GitHub Packages para compilar.
include(":karoo-ext", ":app")
