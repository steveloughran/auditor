package com.github.steveloughran.auditor

import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JarAnalyzerTest {

    @Test
    fun `analyze a JAR with class files`() {
        // Create a temporary JAR containing a real .class file
        val tempJar = Files.createTempFile("test-", ".jar")
        try {
            val classBytes = this::class.java
                .getResourceAsStream("/${this::class.java.name.replace('.', '/')}.class")!!
                .readBytes()

            JarOutputStream(FileOutputStream(tempJar.toFile())).use { jos ->
                jos.putNextEntry(JarEntry("com/github/steveloughran/auditor/JarAnalyzerTest.class"))
                jos.write(classBytes)
                jos.closeEntry()

                // Add a non-class file that should be ignored
                jos.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                jos.write("Manifest-Version: 1.0\n".toByteArray())
                jos.closeEntry()
            }

            val result = JarAnalyzer.analyze(tempJar)
            assertEquals(1, result.size, "Should find exactly one class")
            assertTrue(result.containsKey("com/github/steveloughran/auditor/JarAnalyzerTest"))
        } finally {
            Files.deleteIfExists(tempJar)
        }
    }

    @Test
    fun `analyze empty JAR`() {
        val tempJar = Files.createTempFile("empty-", ".jar")
        try {
            JarOutputStream(FileOutputStream(tempJar.toFile())).use { jos ->
                jos.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                jos.write("Manifest-Version: 1.0\n".toByteArray())
                jos.closeEntry()
            }

            val result = JarAnalyzer.analyze(tempJar)
            assertEquals(0, result.size)
        } finally {
            Files.deleteIfExists(tempJar)
        }
    }
}
