import dev.detekt.gradle.extensions.DetektExtension
import dev.opendungeon.buildlogic.libs
import dev.opendungeon.buildlogic.library
import dev.opendungeon.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * The quality gates every Kotlin module runs as part of `check`: ktlint for formatting, with the Compose
 * rules, and detekt for code smells. Both read their rules from the repository root, so a rule changes
 * in one place. The application and library conventions apply this; modules never apply it themselves.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        pluginManager.apply("dev.detekt")

        extensions.configure<KtlintExtension> {
            version.set(libs.version("ktlint"))
            // Generated sources (Room, Metro, resources) are not ours to format.
            filter { exclude { it.file.path.contains("/build/") } }
        }
        dependencies {
            add("ktlintRuleset", libs.library("compose-rules-ktlint"))
        }

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            parallel.set(true)
            config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
        }
    }
}
