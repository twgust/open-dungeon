import com.android.build.api.dsl.CommonExtension
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import dev.opendungeon.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Enables Compose on an Android module that already applies an application or library convention. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-compose"))
        extensions.getByType<CommonExtension>().buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.library("compose-bom"))
            add("implementation", bom)
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
        }
    }
}
