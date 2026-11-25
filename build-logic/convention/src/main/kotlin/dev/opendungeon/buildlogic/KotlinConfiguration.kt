package dev.opendungeon.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

/**
 * Compiler settings shared by every Kotlin module, JVM or Android.
 *
 * Warnings are errors: a clean build is the bar, not an aspiration.
 */
internal fun KotlinJvmCompilerOptions.applySharedOptions(project: Project) {
    jvmTarget.set(JvmTarget.fromTarget(project.libs.version("jvmTarget")))
    allWarningsAsErrors.set(true)
}

internal fun Project.useJUnitPlatform() {
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
