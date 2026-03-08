package com.github.steveloughran.auditor

import org.assertj.core.api.Assertions.assertThat
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test

class DirectoryScannerTest {

  private fun createJar(dir: Path, relativePath: String, classBytes: ByteArray): Path {
    val jarPath = dir.resolve(relativePath)
    Files.createDirectories(jarPath.parent)
    JarOutputStream(FileOutputStream(jarPath.toFile())).use { jos ->
      jos.putNextEntry(JarEntry("com/example/Test.class"))
      jos.write(classBytes)
      jos.closeEntry()
    }
    return jarPath
  }

  private fun testClassBytes(): ByteArray =
    this::class.java.getResourceAsStream(
      "/${this::class.java.name.replace('.', '/')}.class"
    )!!.readBytes()

  @Test
  fun `findJars discovers nested JARs`() {
    val tempDir = Files.createTempDirectory("scanner-test-")
    try {
      createJar(tempDir, "lib/a.jar", testClassBytes())
      createJar(tempDir, "lib/sub/b.jar", testClassBytes())
      Files.writeString(tempDir.resolve("readme.txt"), "not a jar")

      val jars = DirectoryScanner.findJars(tempDir)
      assertThat(jars).hasSize(2)
      assertThat(jars.keys).containsExactlyInAnyOrder("lib/a.jar", "lib/sub/b.jar")
    } finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `compareDirectories with identical JARs`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      val bytes = testClassBytes()
      createJar(refDir, "lib/test.jar", bytes)
      createJar(tgtDir, "lib/test.jar", bytes)

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      assertThat(report.isMatch).isTrue()
      assertThat(report.results).hasSize(1)
      assertThat(report.results[0].status).isEqualTo(DirectoryScanner.JarPairStatus.IDENTICAL)
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `compareDirectories detects JAR only in reference`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      createJar(refDir, "lib/only-ref.jar", testClassBytes())

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      assertThat(report.isMatch).isFalse()
      assertThat(report.results).hasSize(1)
      assertThat(report.results[0].status)
        .isEqualTo(DirectoryScanner.JarPairStatus.ONLY_IN_REFERENCE)
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `compareDirectories detects JAR only in target`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      createJar(tgtDir, "lib/only-tgt.jar", testClassBytes())

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      assertThat(report.isMatch).isFalse()
      assertThat(report.results).hasSize(1)
      assertThat(report.results[0].status)
        .isEqualTo(DirectoryScanner.JarPairStatus.ONLY_IN_TARGET)
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `compareDirectories detects different JARs`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      // Use different class bytes by including different classes
      val refBytes = this::class.java.getResourceAsStream(
        "/${this::class.java.name.replace('.', '/')}.class"
      )!!.readBytes()
      val tgtBytes = String::class.java.getResourceAsStream(
        "/${String::class.java.name.replace('.', '/')}.class"
      )!!.readBytes()

      createJar(refDir, "lib/test.jar", refBytes)
      createJar(tgtDir, "lib/test.jar", tgtBytes)

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      assertThat(report.isMatch).isFalse()
      assertThat(report.results).hasSize(1)
      assertThat(report.results[0].status)
        .isEqualTo(DirectoryScanner.JarPairStatus.DIFFERENT)
      assertThat(report.results[0].report).isNotNull
      assertThat(report.results[0].report!!.differences).isNotEmpty
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `text output includes JAR filenames`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      createJar(refDir, "lib/common.jar", testClassBytes())
      createJar(tgtDir, "lib/common.jar", testClassBytes())
      createJar(refDir, "lib/only-ref.jar", testClassBytes())

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      val text = report.text()
      assertThat(text).contains("lib/common.jar")
      assertThat(text).contains("lib/only-ref.jar")
      assertThat(text).contains("only in reference")
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `csv output includes JAR column`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      createJar(refDir, "lib/only-ref.jar", testClassBytes())

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      val csv = report.csv()
      assertThat(csv.lines()[0]).isEqualTo("\"jar\",\"class\",\"change\",\"symbol\"")
      assertThat(csv).contains("lib/only-ref.jar")
      assertThat(csv).contains("only in reference")
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun `markdown output includes JAR summary table`() {
    val refDir = Files.createTempDirectory("ref-")
    val tgtDir = Files.createTempDirectory("tgt-")
    try {
      createJar(refDir, "lib/a.jar", testClassBytes())
      createJar(tgtDir, "lib/a.jar", testClassBytes())

      val report = DirectoryScanner.compareDirectories(refDir, tgtDir, AuditLevel.STRUCTURAL)
      val md = report.markdown()
      assertThat(md).contains("## Directory Comparison Report")
      assertThat(md).contains("| JAR | Status |")
      assertThat(md).contains("lib/a.jar")
    } finally {
      refDir.toFile().deleteRecursively()
      tgtDir.toFile().deleteRecursively()
    }
  }
}
