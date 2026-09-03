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

rootProject.name = "aicli"

include(":app")
include(":core")
include(":terminal")
include(":runtime")
include(":provider-api")
include(":provider-claude")
include(":provider-codex")
include(":provider-opencode")
include(":provider-antigravity")
