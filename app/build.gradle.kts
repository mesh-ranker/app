import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Версия задаётся CI через переменные окружения. Локально остаются значения по умолчанию.
val buildVersionCode = providers.environmentVariable("VERSION_CODE").orNull
    ?.toIntOrNull()
    ?: 1
val buildVersionName = providers.environmentVariable("VERSION_NAME").orNull
    ?.takeIf { it.isNotBlank() }
    ?: "1.0.0"

// Постоянная release-подпись из GitHub Actions secrets.
val signingStorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasEnvironmentSigning = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.meshrating.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.meshrating.app"
        minSdk = 24
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        // Интерфейс и ядро лежат в общем каталоге репозитория (shared/ui, shared/core)
        // и копируются в assets скриптом android/tools/sync-ui.sh
        resourceConfigurations += listOf("ru", "en")
    }

    signingConfigs {
        if (hasEnvironmentSigning) {
            create("release") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        } else {
            // Локальная подпись: keystore.properties (файл не хранится в репозитории).
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
                create("release") {
                    storeFile = file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

/**
 * Интерфейс и ядро живут в общем каталоге репозитория (shared/ui, shared/core),
 * поэтому перед сборкой копируем их в assets приложения. Так одна и та же
 * вёрстка работает и в Electron, и в Android WebView.
 */
val syncSharedUi by tasks.registering(Sync::class) {
    description = "Копирует shared/ui и shared/core в assets приложения"
    into(layout.projectDirectory.dir("src/main/assets"))
    from(rootProject.layout.projectDirectory.dir("shared/ui")) {
        into("ui")
    }
    from(rootProject.layout.projectDirectory.dir("shared/core")) {
        into("core")
    }
    doFirst {
        logger.lifecycle("Синхронизация shared/ui → app/src/main/assets/ui")
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(syncSharedUi)
}
tasks.named("preBuild") {
    dependsOn(syncSharedUi)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work)
    implementation(libs.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json) // org.json для JVM-тестов ядра
}
