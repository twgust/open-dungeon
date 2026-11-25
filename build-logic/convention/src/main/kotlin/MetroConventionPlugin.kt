import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Applies the Metro compiler plugin. Metro aggregates contributions across modules, so every module that contributes bindings applies this. */
class MetroConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("metro"))
    }
}
