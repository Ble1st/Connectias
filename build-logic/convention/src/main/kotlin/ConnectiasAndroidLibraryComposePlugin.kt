import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class ConnectiasAndroidLibraryComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                // Note: Using direct versions in build-logic (composite build evaluated early)
                // Version matches gradle/libs.versions.toml: composeBom = "2026.01.00" (libs.versions.composeBom)
                add("implementation", platform("androidx.compose:compose-bom:2026.01.00"))
                add("implementation", "androidx.compose.ui:ui")
                add("implementation", "androidx.compose.ui:ui-graphics")
                add("implementation", "androidx.compose.ui:ui-tooling-preview")
                add("implementation", "androidx.compose.material3:material3")
                add("debugImplementation", "androidx.compose.ui:ui-tooling")
            }
        }
    }
}
