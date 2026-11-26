import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "dev.opendungeon.buildlogic"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get())
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.metro.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.opendungeon.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.opendungeon.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.opendungeon.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.opendungeon.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.opendungeon.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("quality") {
            id = "opendungeon.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("metro") {
            id = libs.plugins.opendungeon.metro.get().pluginId
            implementationClass = "MetroConventionPlugin"
        }
    }
}
