package dev.fireforge.sauron.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class DocSyncTest {

    private fun repoRoot(): File {
        val dir = File(System.getProperty("user.dir"))
        return if (dir.name == "app") dir.parentFile else dir
    }

    private fun docRow(doc: String, label: String): String? =
        Regex("""\|\s*$label\s*\|\s*([^|]+?)\s*\|""").find(doc)?.groupValues?.get(1)

    private fun tomlVersion(toml: String, key: String): String? =
        Regex("""^$key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(toml)?.groupValues?.get(1)

    @Test
    fun `stack table in architecture doc matches the build files`() {
        val root = repoRoot()
        val doc = File(root, "docs/architecture.md").readText()
        val appBuild = File(root, "app/build.gradle.kts").readText()
        val toml = File(root, "gradle/libs.versions.toml").readText()

        fun assertRowMatches(label: String, expected: String, source: String) {
            val actual = docRow(doc, label)
            assertNotNull("doc row for $label missing", actual)
            assertEquals("doc row '$label' ($actual) disagrees with $source ($expected)", expected, actual)
        }

        val compileSdk = Regex("compileSdk\\s*=\\s*(\\d+)").find(appBuild)?.groupValues?.get(1)
        assertNotNull("compileSdk not found in app/build.gradle.kts", compileSdk)
        assertRowMatches("compileSdk", compileSdk!!, "app/build.gradle.kts")

        val minSdk = Regex("minSdk\\s*=\\s*(\\d+)").find(appBuild)?.groupValues?.get(1)
        assertNotNull("minSdk not found in app/build.gradle.kts", minSdk)
        assertRowMatches("minSdk", minSdk!!, "app/build.gradle.kts")

        val agp = tomlVersion(toml, "agp")
        assertNotNull("agp version not found in libs.versions.toml", agp)
        assertRowMatches("Android Gradle Plugin", agp!!, "libs.versions.toml")

        val kotlin = tomlVersion(toml, "composeCompiler")
        assertNotNull("composeCompiler (Kotlin) version not found in libs.versions.toml", kotlin)
        assertRowMatches("Kotlin", kotlin!!, "libs.versions.toml")

        val room = tomlVersion(toml, "room")
        assertNotNull("room version not found in libs.versions.toml", room)
        assertRowMatches("Room", room!!, "libs.versions.toml")
    }
}
