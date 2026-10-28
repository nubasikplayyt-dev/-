plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    // Применяется ниже, только если в app/ лежит google-services.json (без него приложение
    // просто работает без облачных функций, а сборка не ломается).
    id("com.google.gms.google-services") apply false
}

android {
    namespace = "app.lingvo"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.lingvo.island"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Свой ключ подписи берётся из переменных окружения (в репозитории ключей и паролей нет).
    // Он нужен только для входа через Google: Firebase проверяет отпечаток SHA-1 приложения.
    // Без переменных сборка подписывается обычным отладочным ключом, как раньше.
    val keystorePath = System.getenv("LINGVO_KEYSTORE_FILE")
    val hasKeystore = !keystorePath.isNullOrBlank() && file(keystorePath).exists()
    if (hasKeystore) {
        signingConfigs.create("lingvo") {
            storeFile = file(keystorePath!!)
            storePassword = System.getenv("LINGVO_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("LINGVO_KEY_ALIAS") ?: "lingvo"
            keyPassword = System.getenv("LINGVO_KEY_PASSWORD") ?: System.getenv("LINGVO_KEYSTORE_PASSWORD")
        }
    }
    val appSigning = if (hasKeystore) signingConfigs.getByName("lingvo") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = appSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = appSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }


    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Вход через Google (необязательный) и обмен прогрессом по коду.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    // Robolectric: проверка миграции Room и сценариев репозитория на настоящем SQLite.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}

// Облачные функции подключаются только при наличии настроек Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
