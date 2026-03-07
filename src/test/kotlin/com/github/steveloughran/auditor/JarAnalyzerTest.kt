package com.github.steveloughran.auditor

import org.assertj.core.api.Assertions.assertThat
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test

class JarAnalyzerTest {

  @Test
  fun `analyze a JAR with class files`() {
    val tempJar = Files.createTempFile("test-", ".jar")
    try {
      val classBytes = this::class.java
        .getResourceAsStream("/${this::class.java.name.replace('.', '/')}.class")!!
        .readBytes()

      JarOutputStream(FileOutputStream(tempJar.toFile())).use { jos ->
        jos.putNextEntry(JarEntry("com/github/steveloughran/auditor/JarAnalyzerTest.class"))
        jos.write(classBytes)
        jos.closeEntry()

        jos.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
        jos.write("Manifest-Version: 1.0\n".toByteArray())
        jos.closeEntry()
      }

      val result = JarAnalyzer.analyze(tempJar)
      assertThat(result).hasSize(1)
      assertThat(result).containsKey("com/github/steveloughran/auditor/JarAnalyzerTest")
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
      assertThat(result).isEmpty()
    } finally {
      Files.deleteIfExists(tempJar)
    }
  }
}
