package com.github.steveloughran.auditor

import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * Extracts and parses all .class files from a JAR archive.
 */
object JarAnalyzer {

  /**
   * Compute the MD5 hex digest of a file.
   */
  fun md5(path: Path): String {
    val digest = MessageDigest.getInstance("MD5")
    path.toFile().inputStream().use { input ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Parse all .class entries in a JAR, returning a map of
   * class name (internal form, e.g. "com/example/Foo") to its structure.
   */
  fun analyze(
    jarPath: Path,
    level: AuditLevel = AuditLevel.STRUCTURAL,
  ): Map<String, ClassStructure> {
    val result = mutableMapOf<String, ClassStructure>()
    JarFile(jarPath.toFile()).use { jar ->
      for (entry in jar.entries()) {
        if (entry.name.endsWith(".class")) {
          val bytes = jar.getInputStream(entry).use { it.readBytes() }
          val structure = ClassFileParser.parse(bytes, level)
          result[structure.className] = structure
        }
      }
    }
    return result
  }
}
