import com.android.build.api.dsl.CommonExtension
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import dev.opendungeon.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Enables Compose on an Android module that already applies an application or library convention.
 * Domain models are declared stable in `compose-stability.conf`, so screens render them without mirror types.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.getByType<CommonExtension>().buildFeatures.compose = true
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(isolated.rootProject.projectDirectory.file("compose-stability.conf"))
        }

        dependencies {
            val bom = platform(libs.library("compose-bom"))
            add("implementation", bom)
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
        }
    }
}
