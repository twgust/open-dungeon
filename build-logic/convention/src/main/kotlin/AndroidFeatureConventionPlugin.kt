import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: an Android library with Compose, Metro and the MVI foundation.
 * Features depend on core modules, never on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("opendungeon.android.library")
        pluginManager.apply("opendungeon.android.compose")
        pluginManager.apply("opendungeon.metro")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:mvi"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("compose-material3"))
            add("implementation", libs.library("kotlinx-collections-immutable"))
            add("implementation", libs.library("metrox-viewmodel"))
        }
    }
}
