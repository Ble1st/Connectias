import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class ConnectiasJacocoPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")

            extensions.configure<JacocoPluginExtension> {
                toolVersion = "0.8.12"
            }

            val androidComponents = extensions.findByType(AndroidComponentsExtension::class.java)
            androidComponents?.onVariants { variant ->
                val variantName = variant.name
                val testTaskName = "test${variantName.capitalize()}UnitTest"

                tasks.register("jacoco${variantName.capitalize()}Report", JacocoReport::class.java) {
                    dependsOn(testTaskName)
                    group = "verification"
                    description = "Generate Jacoco coverage report for $variantName variant"

                    reports {
                        xml.required.set(true)
                        html.required.set(true)
                    }

                    val excludes = listOf(
                        "**/R.class",
                        "**/R$*.class",
                        "**/BuildConfig.*",
                        "**/Manifest*.*",
                        "**/*Test*.*",
                        "android/**/*.*",
                        "**/*_Hilt*.*",
                        "**/hilt_aggregated_deps/**",
                        "**/*_Factory.*",
                        "**/*_MembersInjector.*",
                        "**/*Module.*",
                        "**/*Dagger*.*",
                        "**/*_Provide*Factory*.*"
                    )

                    val javaTree = fileTree("${buildDir}/intermediates/javac/$variantName/classes") {
                        setExcludes(excludes)
                    }
                    val kotlinTree = fileTree("${buildDir}/tmp/kotlin-classes/$variantName") {
                        setExcludes(excludes)
                    }

                    classDirectories.setFrom(files(javaTree, kotlinTree))
                    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
                    executionData.setFrom(fileTree(buildDir) {
                        include("**/*.exec", "**/*.ec")
                    })
                }
            }
        }
    }
}
