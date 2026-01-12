plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    val buildDirPath = layout.buildDirectory.asFile.get().path

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Use a gradle property to avoid version catalog access at this level.
        version.set(providers.gradleProperty("ktlintVersion"))
        android.set(true)
        filter {
            exclude("**/build/**")
            exclude("**/generated/**")
            exclude { it.file.path.contains(buildDirPath) }
        }
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>().configureEach {
        exclude("**/build/**")
        exclude("**/generated/**")
        setSource(
            fileTree(projectDir) {
                include("**/*.kt")
                exclude("**/build/**")
                exclude("**/generated/**")
            },
        )
        doFirst {
            setSource(source.filter { !it.absolutePath.contains("/build/") })
        }
    }
    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
        exclude("**/build/**")
        exclude("**/generated/**")
        setSource(
            fileTree(projectDir) {
                include("**/*.kt")
                exclude("**/build/**")
                exclude("**/generated/**")
            },
        )
        doFirst {
            setSource(source.filter { !it.absolutePath.contains("/build/") })
        }
    }


    tasks.register("ktLintCheck") {
        dependsOn("ktlintCheck")
    }
}
