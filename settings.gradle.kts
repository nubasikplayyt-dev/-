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
    }
}

val coreOnly = providers.gradleProperty("coreOnly").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (coreOnly) {
            // Зеркало Maven Central на Gradle Plugin Portal — для окружений без Google Maven.
            gradlePluginPortal()
        } else {
            google()
        }
        mavenCentral()
    }
}

rootProject.name = "LingvoIsland"

// Чистая Kotlin-логика (FSRS, импорт, валидация, расписание) — собирается без Android SDK.
include(":core")

// Android-приложение. Запуск с -PcoreOnly=true позволяет собрать и протестировать
// только ядро в окружении без Android SDK.
if (!coreOnly) {
    include(":app")
}
