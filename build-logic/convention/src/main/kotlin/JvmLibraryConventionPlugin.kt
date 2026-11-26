import dev.opendungeon.buildlogic.applySharedOptions
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import dev.opendungeon.buildlogic.pluginId
import dev.opendungeon.buildlogic.useJUnitPlatform
import dev.opendungeon.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** A pure Kotlin/JVM module: no Android framework, fast unit tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        pluginManager.apply("opendungeon.quality")

        extensions.configure<JavaPluginExtension> {
            val javaVersion = org.gradle.api.JavaVersion.toVersion(libs.version("jvmTarget"))
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
        extensions.configure<KotlinJvmProjectExtension> {
            explicitApi()
            compilerOptions.applySharedOptions(target)
        }

        useJUnitPlatform()
        dependencies {
            add("testImplementation", platform(libs.library("junit-bom")))
            add("testImplementation", libs.library("junit-jupiter"))
            add("testImplementation", libs.library("kotlin-test-junit5"))
            add("testRuntimeOnly", libs.library("junit-platform-launcher"))
        }
    }
}
