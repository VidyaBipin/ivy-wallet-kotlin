dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        gradlePluginPortal()
    }
}
rootProject.name = "Ivy Wallet"
include(":app")
include(":ivy-design")
include(":ivy-core")