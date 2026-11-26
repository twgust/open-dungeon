import com.android.build.api.dsl.ApplicationExtension
import dev.opendungeon.buildlogic.configureAndroid
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.pluginId
import dev.opendungeon.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        pluginManager.apply("opendungeon.quality")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
        }
    }
}
