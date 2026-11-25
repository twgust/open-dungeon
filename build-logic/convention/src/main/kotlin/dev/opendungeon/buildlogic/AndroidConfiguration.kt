package dev.opendungeon.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** SDK levels, Java level and Kotlin options shared by application and library modules. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = libs.version("compileSdk").toInt()
        compileSdkMinor = libs.version("compileSdkMinor").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.apply {
            val javaVersion = JavaVersion.toVersion(libs.version("jvmTarget"))
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.applySharedOptions(this@configureAndroid)
    }
    useJUnitPlatform()
    dependencies {
        add("testImplementation", platform(libs.library("junit-bom")))
        add("testImplementation", libs.library("junit-jupiter"))
        add("testImplementation", libs.library("kotlin-test-junit5"))
        add("testRuntimeOnly", libs.library("junit-platform-launcher"))
    }
}

/** Derives a module's namespace from its Gradle path, e.g. `:core:ui` -> `dev.opendungeon.core.ui`. */
internal val Project.derivedNamespace: String
    get() = "dev.opendungeon" + path.replace(':', '.').replace('-', '_')
