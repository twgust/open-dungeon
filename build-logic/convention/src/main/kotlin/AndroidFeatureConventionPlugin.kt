import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature's UI module: an Android library with Compose, Metro and the MVI foundation.
 *
 * It may depend on `:core:*` modules, on `:domain:*` modules for the interfaces it calls, and on the
 * other modules of its own feature (for example `:feature:newstory:ui` on `:feature:newstory:domain`).
 * It never depends on a `:data:*` module, whose implementations reach it only through the app's graph,
 * nor on another feature. The plugin fails the build when a dependency breaks these rules.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("opendungeon.android.library")
        pluginManager.apply("opendungeon.android.compose")
        pluginManager.apply("opendungeon.metro")

        enforceFeatureBoundaries()

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

    private fun Project.enforceFeatureBoundaries() {
        val feature = path.split(':').getOrNull(2)
            ?: throw GradleException("$path applies the feature convention but is not under :feature")
        val ownFeature = ":feature:$feature"
        val allowedPrefixes = listOf(":core:", ":domain:", "$ownFeature:")
        val modulePath = path
        configurations.configureEach {
            val configuration = name
            dependencies.withType(ProjectDependency::class.java).configureEach {
                val dependencyPath = path
                // AGP adds the module itself to its instrumented test classpaths, hence the own-feature root.
                if (dependencyPath != ownFeature && allowedPrefixes.none { dependencyPath.startsWith(it) }) {
                    throw GradleException(
                        "$modulePath may not depend on $dependencyPath ($configuration). Features depend on :core, " +
                            ":domain and their own modules only; data implementations are bound in :app.",
                    )
                }
            }
        }
    }
}
