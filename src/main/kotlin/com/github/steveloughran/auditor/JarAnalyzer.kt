package com.github.steveloughran.auditor

import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Extracts and parses all .class files from a JAR archive.
 */
object JarAnalyzer {

    /**
     * Parse all .class entries in a JAR, returning a map of
     * class name (internal form, e.g. "com/example/Foo") to its structure.
     */
    fun analyze(jarPath: Path): Map<String, ClassStructure> {
        val result = mutableMapOf<String, ClassStructure>()
        JarFile(jarPath.toFile()).use { jar ->
            for (entry in jar.entries()) {
                if (entry.name.endsWith(".class")) {
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    val structure = ClassFileParser.parse(bytes)
                    result[structure.className] = structure
                }
            }
        }
        return result
    }
}
