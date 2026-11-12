import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Optional, per machine: put build output outside the project folder by setting `buildRoot` in
// local.properties (which isn't committed). Needed when the repo lives in a OneDrive folder:
// OneDrive marks new build folders read-only and locks them while syncing, so Gradle can't
// clean them up. Example: buildRoot=C\:/Users/me/AppData/Local/GuitarTunerBuild
val localProperties = Properties().apply {
    rootDir.resolve("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
localProperties.getProperty("buildRoot")?.let { buildRoot ->
    allprojects {
        val name = if (this == rootProject) "root" else path.removePrefix(":").replace(':', '/')
        layout.buildDirectory.set(file("$buildRoot/$name"))
    }
}
