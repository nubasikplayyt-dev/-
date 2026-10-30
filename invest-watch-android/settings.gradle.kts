pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.11.0"
        id("org.jetbrains.kotlin.android") version "2.2.21"
        id("org.jetbrains.kotlin.jvm") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("com.google.devtools.ksp") version "2.2.21-2.0.5"
        id("com.google.dagger.hilt.android") version "2.57.2"
    }
}

// Запуск с -PcoreOnly=true собирает и тестирует только ядро — без Android SDK
// и без Google Maven. Так ядро проверяется в окружениях, где их нет.
val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (coreOnly) {
            gradlePluginPortal()
        } else {
            google()
        }
        mavenCentral()
    }
}

rootProject.name = "InvestWatch"

include(":core")

if (!coreOnly) {
    include(":app")
}
