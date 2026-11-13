import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        kotlin.srcDir("../app/src/main/java/ru/meshrating/app")
        kotlin.include("LinuxMain.kt", "MeshClient.kt", "MeshCore.kt")
        dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.json)
            implementation(libs.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "ru.meshrating.app.LinuxMainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "mesh-ranker"
            packageVersion = providers.environmentVariable("VERSION_NAME").orElse("1.0.0").get()
            description = "MESH Ranker desktop app"
            vendor = "MESH Ranker"
            linux {
                shortcut = true
                menuGroup = "Education"
            }
        }
    }
}
