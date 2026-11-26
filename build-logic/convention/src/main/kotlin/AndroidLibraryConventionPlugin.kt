import com.android.build.api.dsl.LibraryExtension
import dev.opendungeon.buildlogic.configureAndroid
import dev.opendungeon.buildlogic.derivedNamespace
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** An Android library module. Kotlin support is built into AGP, so no separate Kotlin plugin is applied. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        pluginManager.apply("opendungeon.quality")
        extensions.configure<LibraryExtension> {
            namespace = derivedNamespace
            configureAndroid(this)
        }
    }
}
